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
package io.fluo.api.client;

import io.fluo.api.exceptions.CommitException;

/**
 * Enables users to read and write to Fluo at a certain point in time. Transaction extends {@link TransactionBase} to include the {@link #commit()} and
 * {@link #close()} methods as users are responsible for committing and closing resources when using Transaction.
 */
public interface Transaction extends TransactionBase, AutoCloseable {

  /**
   * Commits the transaction. A {@link CommitException} will be thrown if the commit failed.
   */
  public void commit() throws CommitException;

  /**
   * Closes resources
   */
  @Override
  public void close();
}
