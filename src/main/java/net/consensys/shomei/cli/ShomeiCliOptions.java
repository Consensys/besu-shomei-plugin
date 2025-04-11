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

import com.google.common.base.MoreObjects;
import picocli.CommandLine;

/** Shomei cli options. */
public class ShomeiCliOptions {

  static final ShomeiCliOptions INSTANCE = new ShomeiCliOptions();

  public static final String DEFAULT_SHOMEI_HTTP_HOST = "127.0.0.1";

  public static final int DEFAULT_SHOMEI_HTTP_PORT = 8888;

  public static final Boolean DEFAULT_ENABLE_ZKTRACER = false;

  public static final Boolean DEFAULT_ENABLE_ZKTRACE_COMPARISON = false;

  public static final String OPTION_SHOMEI_HTTP_HOST = "--plugin-shomei-http-host";

  public static final String OPTION_SHOMEI_HTTP_PORT = "--plugin-shomei-http-port";

  public static final String OPTION_SHOMEI_ENABLE_ZKTRACER = "--plugin-shomei-enable-zktracer";

  public static final String OPTION_SHOMEI_ENABLE_ZKTRACE_COMPARISON =
      "--plugin-shomei-enable-zktrace-comparison";

  @CommandLine.Option(
      names = {OPTION_SHOMEI_HTTP_HOST},
      hidden = true,
      defaultValue = DEFAULT_SHOMEI_HTTP_HOST,
      paramLabel = "<STRING>",
      description = "HTTP host to push shomei trielogs to")
  // TODO final?
  public String shomeiHttpHost = DEFAULT_SHOMEI_HTTP_HOST;

  @CommandLine.Option(
      names = {OPTION_SHOMEI_HTTP_PORT},
      hidden = true,
      defaultValue = "8888",
      paramLabel = "<INTEGER>",
      description = "HTTP host port to push shomei trielogs to")
  // TODO final?
  public Integer shomeiHttpPort = DEFAULT_SHOMEI_HTTP_PORT;

  @CommandLine.Option(
      names = {OPTION_SHOMEI_ENABLE_ZKTRACER},
      hidden = true,
      defaultValue = "false",
      paramLabel = "<BOOLEAN>",
      description = "Use zkTracer on block import")
  // TODO final?
  public Boolean enableZkTracer = DEFAULT_ENABLE_ZKTRACER;

  @CommandLine.Option(
      names = {OPTION_SHOMEI_ENABLE_ZKTRACE_COMPARISON},
      hidden = true,
      defaultValue = "false",
      paramLabel = "<BOOLEAN>",
      description = "Compare zkTracer state to accumulator on block import")
  // TODO final?
  public Boolean enableZkTraceComparison = DEFAULT_ENABLE_ZKTRACE_COMPARISON;

  private ShomeiCliOptions() {}

  public static ShomeiCliOptions create() {
    return INSTANCE;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("shomeiHttpHost", shomeiHttpHost)
        .add("shomeiHttpPort", shomeiHttpPort)
        .add("shomeiEnableZkTracer", enableZkTracer)
        .add("shomeiEnableZkTraceComparison", enableZkTraceComparison)
        .toString();
  }
}
