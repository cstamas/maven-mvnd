/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mvndaemon.mvnd.it;

import javax.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.MatchInOrderAmongOthers;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.junit.MvndNativeTest;

@MvndNativeTest(projectDir = "src/test/projects/single-module")
class SingleModuleNativeIT {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {
        final Path helloFilePath = parameters.multiModuleProjectDirectory().resolve("target/hello.txt");
        if (Files.exists(helloFilePath)) {
            Files.delete(helloFilePath);
        }

        final Path localMavenRepo = parameters.mavenRepoLocal();
        final Path installedJar = localMavenRepo.resolve(
                "org/mvndaemon/mvnd/test/single-module/single-module/0.0.1-SNAPSHOT/single-module-0.0.1-SNAPSHOT.jar");
        Assertions.assertThat(installedJar).doesNotExist();

        final TestClientOutput o = new TestClientOutput();
        client.execute(o, "clean", "install", "-e", "-B").assertSuccess();
        final Map<String, String> props =
                MvndTestUtil.properties(parameters.multiModuleProjectDirectory().resolve("pom.xml"));

        final List<String> messages = o.getMessages().stream()
                .filter(m -> m.getType() != Message.MOJO_STARTED)
                .map(m -> m.toString())
                .collect(Collectors.toList());
        Assertions.assertThat(messages)
                .is(new MatchInOrderAmongOthers<>(
                        "Building single-module",
                        mojoStartedLogMessage(props, "maven-clean-plugin", "clean", "default-clean"),
                        mojoStartedLogMessage(props, "maven-resources-plugin", "resources", "default-resources"),
                        mojoStartedLogMessage(props, "maven-compiler-plugin", "compile", "default-compile"),
                        mojoStartedLogMessage(
                                props, "maven-resources-plugin", "testResources", "default-testResources"),
                        mojoStartedLogMessage(props, "maven-compiler-plugin", "testCompile", "default-testCompile"),
                        mojoStartedLogMessage(props, "maven-surefire-plugin", "test", "default-test"),
                        mojoStartedLogMessage(props, "maven-install-plugin", "install", "default-install"),
                        "BUILD SUCCESS"));

        assertJVM(o, props);

        /* The target/hello.txt is created by HelloTest */
        Assertions.assertThat(helloFilePath).exists();

        Assertions.assertThat(installedJar).exists();
    }

    protected void assertJVM(TestClientOutput o, Map<String, String> props) {
        /* implemented in the subclass */
    }

    String mojoStartedLogMessage(Map<String, String> props, String pluginArtifactId, String mojo, String executionId) {
        return "\\Q--- "
                + pluginArtifactId.replace("maven-", "").replace("-plugin", "")
                + ":" + props.get(pluginArtifactId + ".version") + ":" + mojo + " ("
                + executionId + ") @ single-module ---\\E";
    }
}
