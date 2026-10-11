package com.example.docmind;

import com.example.docmind.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocmindApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoadsAndFlywayCreatesSchema() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isEqualTo(3);
        for (String table : new String[]{"users", "documents", "refresh_tokens", "conversations", "messages", "message_citations", "vector_store"}) {
            assertThat(jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class, "public." + table))
                    .as(table).isTrue();
        }
    }
}
