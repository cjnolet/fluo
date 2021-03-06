/*
 * Copyright 2014 Fluo authors (see AUTHORS)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fluo.core.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.fluo.accumulo.format.FluoFormatter;
import io.fluo.api.config.ScannerConfiguration;
import io.fluo.api.data.Bytes;
import io.fluo.api.data.Column;
import io.fluo.api.exceptions.CommitException;
import io.fluo.api.iterator.ColumnIterator;
import io.fluo.api.iterator.RowIterator;
import io.fluo.api.types.StringEncoder;
import io.fluo.api.types.TypeLayer;
import io.fluo.core.TestBaseImpl;
import io.fluo.core.TestTransaction;
import io.fluo.core.exceptions.StaleScanException;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.util.Stat;
import org.apache.hadoop.io.Text;
import org.junit.Assert;
import org.junit.Test;
/**
 * This test starts multiple thread that randomly transfer between accounts. At any given time the sum of all money in the bank should be the same, therefore
 * the average should not vary.
 */
public class StochasticBankIT extends TestBaseImpl {
  
  static TypeLayer typeLayer = new TypeLayer(new StringEncoder());
  private static AtomicInteger txCount = new AtomicInteger();

  @SuppressWarnings("resource")
  @Test
  public void testConcurrency() throws Exception {

    conn.tableOperations().setProperty(table, Property.TABLE_MAJC_RATIO.getKey(), "1");
    conn.tableOperations().setProperty(table, Property.TABLE_BLOCKCACHE_ENABLED.getKey(), "true");
    
    int numAccounts = 5000;
    
    TreeSet<Text> splits = new TreeSet<>();
    splits.add(new Text(fmtAcct(numAccounts / 4)));
    splits.add(new Text(fmtAcct(numAccounts / 2)));
    splits.add(new Text(fmtAcct(3 * numAccounts / 4)));

    conn.tableOperations().addSplits(table, splits);

    AtomicBoolean runFlag = new AtomicBoolean(true);
    
    populate(env, numAccounts);
    
    Random rand = new Random();
    Environment tenv = env;

    if (rand.nextBoolean())
      tenv = new FaultyConfig(env, (rand.nextDouble() * .4) + .1, .50);

    List<Thread> threads = startTransfers(tenv, numAccounts, 20, runFlag);
    
    runVerifier(env, numAccounts, 100);
    
    runFlag.set(false);
    
    for (Thread thread : threads) {
      thread.join();
    }
    
    System.out.println("txCount : " + txCount.get());
    Assert.assertTrue("txCount : " + txCount.get(), txCount.get() > 0);
    
    runVerifier(env, numAccounts, 1);
  }

  private static Column balanceCol = typeLayer.bc().fam("data").qual("balance").vis();

  private static void populate(Environment env, int numAccounts) throws Exception {
    TestTransaction tx = new TestTransaction(env);
    
    for(int i = 0; i < numAccounts; i++){
      tx.mutate().row(fmtAcct(i)).col(balanceCol).set(1000);
    }
    
    tx.done();
  }
  
  private static String fmtAcct(int i) {
    return String.format("%09d", i);
  }

  private static List<Thread> startTransfers(final Environment env, final int numAccounts, int numThreads, final AtomicBoolean runFlag) {
    
    ArrayList<Thread> threads = new ArrayList<>();
    
    for (int i = 0; i < numThreads; i++) {
      Runnable task = new Runnable() {
        Random rand = new Random();
        
        @Override
        public void run() {
          while (runFlag.get()) {
            int acct1 = rand.nextInt(numAccounts);
            int acct2 = rand.nextInt(numAccounts);
            while (acct1 == acct2)
              acct2 = rand.nextInt(numAccounts);
            int amt = rand.nextInt(100);
            
            transfer(env, fmtAcct(acct1), fmtAcct(acct2), amt);
          }
          
        }
        
      };
      Thread thread = new Thread(task);
      thread.start();
      threads.add(thread);
    }
    
    return threads;
  }
  
  private static void transfer(Environment env, String from, String to, int amt) {
    try {
      
      while (true) {
        try {
          TestTransaction tx = new TestTransaction(env);
          int bal1 = tx.get().row(from).col(balanceCol).toInteger();
          int bal2 = tx.get().row(to).col(balanceCol).toInteger();
          
          if (bal1 - amt >= 0) {
            tx.mutate().row(from).col(balanceCol).set(bal1 - amt);
            tx.mutate().row(to).col(balanceCol).set(bal2 + amt);
          } else {
            break;
          }
          
          tx.done();
          break;

        } catch (StaleScanException sse) {
          // retry
        } catch (CommitException ce) {
          // retry
        }
      }
      
      txCount.incrementAndGet();
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private static void runVerifier(Environment env, int numAccounts, int num) {
    TestTransaction lastTx = null;

    try {

      for (int i = 0; i < num; i++) {
        
        if (i == num / 2) {
          env.getConnector().tableOperations().compact(env.getTable(), null, null, true, false);
        }

        long t1 = System.currentTimeMillis();

        TestTransaction tx = new TestTransaction(env);
        RowIterator iter = tx.get(new ScannerConfiguration());
        
        Stat stat = new Stat();

        while (iter.hasNext()) {
          Entry<Bytes,ColumnIterator> colIter = iter.next();
          Entry<Column,Bytes> column = colIter.getValue().next();
          
          int amt = Integer.parseInt(column.getValue().toString());
          
          stat.addStat(amt);

        }
        
        long t2 = System.currentTimeMillis();

        System.out.printf("avg : %,9.2f  min : %,6d  max : %,6d  stddev : %1.2f  rate : %,6.2f\n", stat.getAverage(), stat.getMin(), stat.getMax(),
            stat.getStdDev(), numAccounts / ((t2 - t1) / 1000.0));

        if (stat.getSum() != numAccounts * 1000) {
          if (lastTx != null)
            printDiffs(env, lastTx, tx);
        }
        
        Assert.assertEquals(numAccounts * 1000, stat.getSum());

        lastTx = tx;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private static void printDiffs(Environment env, TestTransaction lastTx, TestTransaction tx) throws Exception {
    Map<String,String> bals1 = toMap(lastTx);
    Map<String,String> bals2 = toMap(tx);
    
    if (!bals1.keySet().equals(bals2.keySet())) {
      System.out.print("KS NOT EQ");
    }
    
    int sum1 = 0;
    int sum2 = 0;

    for (Entry<String,String> entry : bals1.entrySet()) {
      String val2 = bals2.get(entry.getKey());
      
      if (!entry.getValue().equals(val2)) {
        int v1 = Integer.parseInt(entry.getValue());
        int v2 = Integer.parseInt(val2);
        sum1 += v1;
        sum2 += v2;
        
        System.out.println(entry.getKey() + " " + entry.getValue() + " " + val2 + " " + (v2 - v1));
      }
    }

    System.out.println("start times : " + lastTx.getStartTs() + " " + tx.getStartTs());
    System.out.printf("sum1 : %,d  sum2 : %,d  diff : %,d\n", sum1, sum2, sum2 - sum1);
    
    File tmpFile = File.createTempFile("sb_dump", ".txt");
    Writer fw = new BufferedWriter(new FileWriter(tmpFile));
    
    Scanner scanner = env.getConnector().createScanner(env.getTable(), env.getAuthorizations());
    FluoFormatter af = new FluoFormatter();
    af.initialize(scanner, true);
    
    while (af.hasNext()) {
      fw.append(af.next());
      fw.append("\n");
    }
    
    fw.close();
    
    System.out.println("Dumped table : " + tmpFile);

  }
  
  private static HashMap<String,String> toMap(TestTransaction tx) throws Exception {
    HashMap<String,String> map = new HashMap<>();
    
    RowIterator iter = tx.get(new ScannerConfiguration());
    while (iter.hasNext()) {
      Entry<Bytes,ColumnIterator> colIter = iter.next();
      Entry<Column,Bytes> column = colIter.getValue().next();
      
      map.put(colIter.getKey().toString(), column.getValue().toString());
    }
    
    return map;
  }
}
