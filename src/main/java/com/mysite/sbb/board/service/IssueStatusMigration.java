package com.mysite.sbb.board.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

// [ISS-04] 이슈 상태 데이터 이전 (시작할 때마다 실행해도 결과가 같다 : 멱등)
//  1) 예전 스키마는 issue_status 가 ENUM('OPEN','IN_PROGRESS','RESOLVED') 이거나 CHECK 제약이 걸려 있어
//     새 값(REPORTED ...)을 넣을 수 없다. ddl-auto:update 는 이 타입을 바꾸지 않으므로 VARCHAR 로 넓힌다
//  2) OPEN → REPORTED (IN_PROGRESS, RESOLVED 는 이름이 같아 그대로)
//  스키마가 만들어진 뒤, 그리고 어떤 화면·스케줄러가 Post 를 읽기 전에 돌도록 빈 초기화 단계에서 실행한다
@Slf4j
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class IssueStatusMigration implements InitializingBean {

    private final DataSource dataSource;

    @Override
    public void afterPropertiesSet() throws SQLException {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        boolean mysql;
        try (Connection c = dataSource.getConnection()) {
            mysql = c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        }
        String schema = mysql ? "database()" : "schema()";

        List<String> types = jdbc.queryForList("select data_type from information_schema.columns "
                + "where table_schema = " + schema + " and lower(table_name) = 'post' and lower(column_name) = 'issue_status'", String.class);
        if (types.isEmpty()) return;

        if ("enum".equalsIgnoreCase(types.get(0))) {
            jdbc.execute(mysql ? "alter table post modify column issue_status varchar(20) null"
                    : "alter table post alter column issue_status varchar(20)");
            log.info("=======> 이슈 상태 컬럼을 VARCHAR(20) 으로 넓혔어요");
        }

        List<String> checks = jdbc.queryForList("select tc.constraint_name from information_schema.table_constraints tc "
                + "join information_schema.check_constraints cc on cc.constraint_schema = tc.constraint_schema "
                + "and cc.constraint_name = tc.constraint_name "
                + "where tc.table_schema = " + schema + " and lower(tc.table_name) = 'post' and tc.constraint_type = 'CHECK' "
                + "and lower(cc.check_clause) like '%issue_status%'", String.class);
        for (String name : checks) {
            jdbc.execute("alter table post " + (mysql ? "drop check `" + name + "`" : "drop constraint \"" + name + "\""));
            log.info("=======> 이슈 상태 CHECK 제약 {} 제거", name);
        }

        int moved = jdbc.update("update post set issue_status = 'REPORTED' where issue_status = 'OPEN'");
        if (moved > 0) log.info("=======> 이슈 {}건을 OPEN → REPORTED 로 옮겼어요", moved);
    }
}
