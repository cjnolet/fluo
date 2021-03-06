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
package io.fluo.accumulo.values;

import io.fluo.accumulo.util.ByteArrayUtil;

public class DelLockValue {
  private final boolean primary;
  private final boolean rollback;
  private final long lockTime;
  
  public DelLockValue(byte[] data) {
    primary = isPrimary(data);
    rollback = isRollback(data);
    lockTime = getTimestamp(data);
  }
  
  public long getTimestamp() {
    return lockTime;
  }
  
  public boolean isPrimary() {
    return primary;
  }

  public boolean isRollback() {
    return rollback;
  }
  
  public static byte[] encode(long ts, boolean primary, boolean rollback) {
    byte ba[] = new byte[9];
    ba[0] = (byte) ((primary ? 1 : 0) | (rollback ? 2 : 0));
    ByteArrayUtil.encode(ba, 1, ts);
    return ba;
  }
  
  @Override
  public String toString() {
    return (rollback ? "ABORT " : "COMMIT ") + (primary ? "PRIMARY " : "") + lockTime;
  }
  
  public static boolean isPrimary(byte[] data) {
    return (data[0] & 0x01) == 1;
  }
  
  public static boolean isRollback(byte[] data) {
    return (data[0] & 0x02) == 2;
  }
  
  public static long getTimestamp(byte[] data) {
    return ByteArrayUtil.decodeLong(data, 1);
  }

}