-- ddl-auto가 만드는 테이블만으로는 재현되지 않는 수동 제약을 테스트 DB에도 적용한다
-- (로컬 개발 DB에는 이미 수동으로 적용돼 있음). Hibernate DDL 이후 실행되도록
-- spring.jpa.defer-datasource-initialization=true로 순서를 미뤄뒀다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_release_cases_active_plan
    ON release_cases (plan_id)
    WHERE canceled_at IS NULL;
