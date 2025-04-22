/*
 * Copyright ConsenSys 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package net.consensys.shomei.cli;

import java.util.EnumSet;

import com.google.common.base.MoreObjects;
import picocli.CommandLine;

/** Shomei cli options. */
public class ShomeiCliOptions {

  public static final String DEFAULT_SHOMEI_HTTP_HOST = "127.0.0.1";

  public static final int DEFAULT_SHOMEI_HTTP_PORT = 8888;

  public static final boolean DEFAULT_ENABLE_ZKTRACER = true;

  public static final boolean DEFAULT_ENABLE_ZKTRACE_COMPARISON = false;

  public static final String OPTION_SHOMEI_HTTP_HOST = "--plugin-shomei-http-host";

  public static final String OPTION_SHOMEI_HTTP_PORT = "--plugin-shomei-http-port";

  public static final String OPTION_SHOMEI_ENABLE_ZKTRACER = "--plugin-shomei-enable-zktracer";

  public static final String OPTION_SHOMEI_ZKTRACE_COMPARISON_MODE =
      "--plugin-shomei-zktrace-comparison-mode";

  @CommandLine.Option(
      names = {OPTION_SHOMEI_HTTP_HOST},
      hidden = true,
      defaultValue = DEFAULT_SHOMEI_HTTP_HOST,
      paramLabel = "<STRING>",
      description = "HTTP host to push shomei trielogs to")
  public String shomeiHttpHost = DEFAULT_SHOMEI_HTTP_HOST;

  @CommandLine.Option(
      names = {OPTION_SHOMEI_HTTP_PORT},
      hidden = true,
      defaultValue = "8888",
      paramLabel = "<INTEGER>",
      description = "HTTP host port to push shomei trielogs to")
  public Integer shomeiHttpPort = DEFAULT_SHOMEI_HTTP_PORT;

  @CommandLine.Option(
      names = {OPTION_SHOMEI_ENABLE_ZKTRACER},
      hidden = true,
      defaultValue = "false",
      paramLabel = "<BOOLEAN>",
      description = "Use zkTracer on block import")
  public Boolean enableZkTracer = DEFAULT_ENABLE_ZKTRACER;

  /*
   * use bitmasking to enable/disable comparsion features
   */
  public enum ZkTraceComparisonFeature {
    MISMATCH_LOGGING(1),
    HUB_TO_ACCUMULATOR(2),
    ACCUMULATOR_TO_HUB(4),
    DECORATE_FROM_HUB(8);

    private final int bit;

    ZkTraceComparisonFeature(int bit) {
      this.bit = bit;
    }

    public int getBit() {
      return bit;
    }

    public static EnumSet<ZkTraceComparisonFeature> fromMask(int mask) {
      EnumSet<ZkTraceComparisonFeature> set = EnumSet.noneOf(ZkTraceComparisonFeature.class);
      for (ZkTraceComparisonFeature feature : values()) {
        if ((mask & feature.bit) != 0) {
          set.add(feature);
        }
      }
      return set;
    }

    public static int toMask(EnumSet<ZkTraceComparisonFeature> features) {
      int mask = 0;
      for (ZkTraceComparisonFeature feature : features) {
        mask |= feature.bit;
      }
      return mask;
    }

    public static boolean isEnabled(int mask, ZkTraceComparisonFeature feature) {
      return (mask & feature.bit) != 0;
    }

    public static boolean anyEnabled(final int mask, final ZkTraceComparisonFeature... features) {
      for (var feature : features) {
        if (isEnabled(mask, feature)) {
          return true;
        }
      }
      return false;
    }
  }

  @CommandLine.Option(
      names = {OPTION_SHOMEI_ZKTRACE_COMPARISON_MODE},
      hidden = false,
      paramLabel = "<MASK>",
      description = {
        "Bitmask to enable zkTracer comparison features.",
        "1 = LOGGING",
        "2 = compare HUB→ACCUMULATOR",
        "4 = compare ACCUMULATOR→HUB",
        "8 = DECORATE ACCUMULATOR FROM HUB.",
        "Add values (e.g., 3 = LOGGING + HUB→ACC, default no comparison enabled)."
      })
  public int zkTraceComparisonMask = 0;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("shomei-http-host", shomeiHttpHost)
        .add("shomei-http-port", shomeiHttpPort)
        .add("shomei-enable-zktracer", enableZkTracer)
        .add("shomei-zktrace-comparison-mode", zkTraceComparisonMask)
        .toString();
  }
}
