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
package io.fluo.api.types;

import io.fluo.api.data.Bytes;

/**
 * Transforms Java primitives to and from bytes using desired encoding
 */
public interface Encoder {

  /**
   * Decodes an integer from {@link Bytes}
   */
  public int decodeInteger(Bytes b);

  /**
   * Encodes an integer to {@link Bytes}
   */
  public Bytes encode(int i);

  /**
   * Decodes a long from {@link Bytes}
   */
  public long decodeLong(Bytes b);

  /**
   * Encodes a long to {@link Bytes}
   */
  public Bytes encode(long l);

  /**
   * Decodes a String from {@link Bytes}
   */
  public String decodeString(Bytes b);

  /**
   * Encodes a String to {@link Bytes}
   */
  public Bytes encode(String s);

  /**
   * Decodes a float from {@link Bytes}
   */
  public float decodeFloat(Bytes b);

  /**
   * Encodes a float to {@link Bytes}
   */
  public Bytes encode(float f);

  /**
   * Decodes a double from {@link Bytes}
   */
  public double decodeDouble(Bytes b);

  /**
   * Encodes a double to {@link Bytes}
   */
  public Bytes encode(double d);

  /**
   * Decodes a boolean from {@link Bytes}
   */
  public boolean decodeBoolean(Bytes b);

  /**
   * Encodes a boolean to {@link Bytes}
   */
  public Bytes encode(boolean b);
}
