/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.r2dbc.h2;

import io.r2dbc.h2.util.H2ServerExtension;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.test.TestKit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jdbc.core.JdbcOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

import static io.r2dbc.h2.H2ConnectionFactoryProvider.H2_DRIVER;
import static io.r2dbc.h2.H2ConnectionFactoryProvider.URL;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

final class H2Example {

    @RegisterExtension
    static final H2ServerExtension SERVER = new H2ServerExtension();

    private final ConnectionFactory connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
        .option(DRIVER, H2_DRIVER)
        .option(PASSWORD, SERVER.getPassword())
        .option(URL, SERVER.getUrl())
        .option(USER, SERVER.getUsername())
        .build());

    @Nested
    @Disabled("TODO: Fix H2Statement so it properly handles plain JDBC placeholders.")
    final class JdbcStyle implements TestKit<Integer> {

        @Override
        public ConnectionFactory getConnectionFactory() {
            return H2Example.this.connectionFactory;
        }

        @Override
        public Integer getIdentifier(int index) {
            return index;
        }

        @Override
        public JdbcOperations getJdbcOperations() {
            JdbcOperations jdbcOperations = SERVER.getJdbcOperations();

            if (jdbcOperations == null) {
                throw new IllegalStateException("JdbcOperations not yet initialized");
            }

            return jdbcOperations;
        }

        @Override
        public String getPlaceholder(int index) {
            return "?";
        }
    }

    @Nested
    final class NamedParameterStyle implements TestKit<String> {

        @Override
        public ConnectionFactory getConnectionFactory() {
            return H2Example.this.connectionFactory;
        }

        @Override
        public String getIdentifier(int index) {
            return getPlaceholder(index);
        }

        @Override
        public JdbcOperations getJdbcOperations() {
            JdbcOperations jdbcOperations = SERVER.getJdbcOperations();

            if (jdbcOperations == null) {
                throw new IllegalStateException("JdbcOperations not yet initialized.");
            }

            return jdbcOperations;
        }

        @Override
        public String getPlaceholder(int index) {
            return String.format("?%d", index + 1);
        }

        @Test
        @Override
        public void columnMetadata() {
            getJdbcOperations().execute("INSERT INTO test_two_column VALUES (100, 'hello')");

            Mono.from(getConnectionFactory().create())
                .flatMapMany(connection -> Flux.from(connection

                    .createStatement("SELECT col1 AS value, col2 AS value FROM test_two_column")
                    .execute())
                    .flatMap(result -> {
                        return result.map((row, rowMetadata) -> {
                            Collection<String> columnNames = rowMetadata.getColumnNames();
                            return Arrays.asList(rowMetadata.getColumnMetadata("value").getName(), rowMetadata.getColumnMetadata("VALUE").getName(), columnNames.contains("value"), columnNames.contains(
                                "VALUE"));
                        });
                    })
                    .flatMapIterable(Function.identity())
                    .concatWith(close(connection)))
                .as(StepVerifier::create)
                .expectNext("VALUE").as("Column label col1")
                .expectNext("VALUE").as("Column label col1 (get by uppercase)")
                .expectNext(true).as("getColumnNames.contains(value)")
                .expectNext(true).as("getColumnNames.contains(VALUE)")
                .verifyComplete();
        }

        @Override
        public void bindFails() {
            // TODO: Figure out how to perform bind validations that are normally done during execution phase.
        }

        @Override
        public void prepareStatementWithIncompleteBatchFails() {
            // TODO: Figure out how to perform bind validations that are normally done during execution phase.
        }

        @Override
        public void prepareStatementWithIncompleteBindingFails() {
            // TODO: Figure out how to perform bind validations that are normally done during execution phase.
        }

        @Override
        public void returnGeneratedValues() {
            // TODO: Figure out how to insert a column and get the row back instead of rows updated.
        }

        <T> Mono<T> close(Connection connection) {
            return Mono.from(connection
                .close())
                .then(Mono.empty());
        }
    }

    @Nested
    final class PostgresqlStyle implements TestKit<String> {

        @Override
        public ConnectionFactory getConnectionFactory() {
            return H2Example.this.connectionFactory;
        }

        @Override
        public String getIdentifier(int index) {
            return getPlaceholder(index);
        }

        @Override
        public JdbcOperations getJdbcOperations() {
            JdbcOperations jdbcOperations = SERVER.getJdbcOperations();

            if (jdbcOperations == null) {
                throw new IllegalStateException("JdbcOperations not yet initialized");
            }

            return jdbcOperations;
        }

        @Override
        public String getPlaceholder(int index) {
            return String.format("$%d", index + 1);
        }

        @Test
        @Override
        public void columnMetadata() {
            getJdbcOperations().execute("INSERT INTO test_two_column VALUES (100, 'hello')");

            Mono.from(getConnectionFactory().create())
                .flatMapMany(connection -> Flux.from(connection

                    .createStatement("SELECT col1 AS value, col2 AS value FROM test_two_column")
                    .execute())
                    .flatMap(result -> {
                        return result.map((row, rowMetadata) -> {
                            Collection<String> columnNames = rowMetadata.getColumnNames();
                            return Arrays.asList(rowMetadata.getColumnMetadata("value").getName(), rowMetadata.getColumnMetadata("VALUE").getName(), columnNames.contains("value"), columnNames.contains(
                                "VALUE"));
                        });
                    })
                    .flatMapIterable(Function.identity())
                    .concatWith(close(connection)))
                .as(StepVerifier::create)
                .expectNext("VALUE").as("Column label col1")
                .expectNext("VALUE").as("Column label col1 (get by uppercase)")
                .expectNext(true).as("getColumnNames.contains(value)")
                .expectNext(true).as("getColumnNames.contains(VALUE)")
                .verifyComplete();
        }

        @Override
        public void bindFails() {
            // TODO: Figure out how to perform bind validations that are normally done during execution phase.
        }

        @Override
        public void prepareStatementWithIncompleteBatchFails() {
            // TODO: Figure out how to perform bind validations that are normally done during execution phase.
        }

        @Override
        public void prepareStatementWithIncompleteBindingFails() {
            // TODO: Figure out how to perform bind validations that are normally done during execution phase.
        }

        @Override
        public void returnGeneratedValues() {
            // TODO: Figure out how to insert a column and get the row back instead of rows updated.
        }

        <T> Mono<T> close(Connection connection) {
            return Mono.from(connection
                .close())
                .then(Mono.empty());
        }
    }
}
