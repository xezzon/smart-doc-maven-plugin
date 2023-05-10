/*
 * smart-doc https://github.com/smart-doc-group/smart-doc
 *
 * Copyright (C) 2018-2023 smart-doc
 *
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
package com.smartdoc.chain;

import org.apache.maven.artifact.Artifact;

/**
 * @author yu 2020/1/13.
 */
public class CommonArtifactFilterChain implements FilterChain {

    private FilterChain filterChain;

    @Override
    public void setNext(FilterChain nextInChain) {
        this.filterChain = nextInChain;
    }

    @Override
    public boolean ignoreArtifactById(Artifact artifact) {
        String artifactId = artifact.getArtifactId();
        switch (artifactId) {
            case "jsqlparser":
            case "disruptor":
            case "snakeyaml":
            case "spring-boot-autoconfigure":
            case "forest-spring-boot-starter":
            case "HikariCP":
            case "mysql-connector-java":
            case "mysql-connector-j":
            case "classmate":
            case "commons-codec":
            case "commons-lang3":
            case "commons-text":
            case "commons-beanutils":
            case "commons-beanutils-core":
            case "commons-compress":
            case "commons-collections4":
            case "spring-web":
            case "spring-webmvc":
            case "spring-r2dbc":
            case "spring-orm":
            case "spring-data-jpa":
            case "spring-context-support":
            case "spring-aspects":
            case "hibernate-validator":
            case "xstream":
            case "spring-tx":
            case "javassist":
            case "javafaker":
            case "qdox":
            case "gson":
            case "netty-all":
            case "javacv-platform":
            case "antlr4-runtime":
            case "jetty":
            case "velocity":
            case "beetl":
            case "freemarker":
            case "xml-apis":
            case "mchange-commons-java":
            case "druid":
            case "mssql-jdbc":
            case "easyexcel":
            case "zookeeper":
            case "okio":
            case "okhttp":
            case "joda-time":
            case "protobuf-java":
            case "jenkins-client":
            case "jose4j":
            case "gson-fire":
            case "java-jwt":
            case "joda-convert":
            case "kafka-clients":
            case "kubernetes-client":
            case "client-java-proto":
            case "commons-httpclient":
                return true;
            default:
                return this.ignore(filterChain, artifact);
        }
    }
}
