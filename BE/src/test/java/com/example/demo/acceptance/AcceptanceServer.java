package com.example.demo.acceptance;

import com.example.demo.DemoApplication;
import com.example.demo.MutableClock;
import com.example.demo.config.CheckpointSchedule;
import com.example.demo.service.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Test-classpath-only server. It owns its ephemeral PostgreSQL; production URLs are rejected. */
public class AcceptanceServer {
    static final String KEY=UUID.randomUUID().toString();
    static final String PASSWORD="Acceptance-only-2026!";
    static EmbeddedPostgres postgres;
    public static void main(String[] args) throws Exception {
        postgres=EmbeddedPostgres.builder().setPort(0).setServerConfig("listen_addresses","127.0.0.1").setServerConfig("timezone","UTC").start();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{postgres.close();}catch(Exception ignored){}}));
        int port=Integer.getInteger("acceptance.port",18083);
        String jdbc=postgres.getJdbcUrl("postgres","postgres");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/acceptance-runtime.json"),"{\"baseURL\":\"http://127.0.0.1:"+port+"\",\"jdbcUrl\":\""+jdbc+"\",\"key\":\""+KEY+"\",\"friday\":\"2026-11-20\"}");
        new SpringApplicationBuilder(DemoApplication.class,TestConfig.class,Control.class).profiles("acceptance").run(
            "--spring.datasource.url="+jdbc,"--spring.datasource.username=postgres","--spring.datasource.password=",
            "--spring.config.import=optional:classpath:/acceptance-no-local.properties","--server.port="+port,"--server.address=127.0.0.1",
            "--hanare.jobs.enabled=false","--spring.thymeleaf.cache=false","--debug=false",
            "--logging.level.root=WARN","--logging.level.org.springframework=WARN","--logging.level.org.springframework.jdbc=WARN");
    }
    @TestConfiguration(proxyBeanMethods=false) @Profile("acceptance")
    public static class TestConfig {
        @Bean @Primary MutableClock acceptanceClock(){return new MutableClock();}
        @Bean @Order(0) SecurityFilterChain testControlSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/__test/**")
                .authorizeHttpRequests(a->a.anyRequest().access((auth,context)->new AuthorizationDecision(KEY.equals(context.getRequest().getHeader("X-Test-Key")))))
                .csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).build();
        }
        @Bean ApplicationRunner seed(Control control){return args->control.reset();}
    }
    @RestController @Profile("acceptance")
    public static class Control {
        final JdbcTemplate db; final MutableClock clock; final CheckpointJobs jobs; final WeeklyPolicy weeks;
        final Map<String,UUID> aliases=new LinkedHashMap<>(); final String hash;
        public Control(JdbcTemplate db,DataSource source,MutableClock clock,CheckpointJobs jobs,WeeklyPolicy weeks,PasswordEncoder encoder) throws Exception {
            this.db=db;this.clock=clock;this.jobs=jobs;this.weeks=weeks;hash=encoder.encode(PASSWORD);
            try(var connection=source.getConnection()){
                if(!connection.getMetaData().getURL().equals(postgres.getJdbcUrl("postgres","postgres")))
                    throw new IllegalStateException("Refusing test controls on an unowned database");
            }
            for(String key:List.of("P1","P2","TL1","A","B","C","D","E","TL2","OUTSIDER","T_A1","T_A2","T_B1","T_B2","T_C1","T_D1","T_E1","T_DONE","T_P2"))
                aliases.put(key,UUID.nameUUIDFromBytes(("hanare-acceptance:"+key).getBytes(StandardCharsets.UTF_8)));
        }
        UUID id(String alias){return aliases.get(alias);}
        @GetMapping("/__test/fixture") public Object fixture(){return Map.of("aliases",aliases,"friday","2026-11-20","now",weeks.timestamp(),"jdbcUrl",postgres.getJdbcUrl("postgres","postgres"));}
        @PostMapping("/__test/reset") @Transactional
        public Object reset(){
            clock.set("2026-11-20T08:29:00Z");
            db.execute("TRUNCATE projects, users CASCADE");
            for(String alias:List.of("TL1","A","B","C","D","E","TL2","OUTSIDER")){
                String name=alias.startsWith("TL")?"Leader "+alias:"Member "+alias;
                db.update("INSERT INTO users(id,name,email,password_hash) VALUES (?,?,?,?)",id(alias),name,alias.toLowerCase(Locale.ROOT)+"@hanare.test",hash);
            }
            db.update("INSERT INTO projects(id,name) VALUES (?,?),(?,?)",id("P1"),"Hanare Demo P1",id("P2"),"Another Project P2");
            for(String alias:List.of("TL1","A","B","C","D","E","TL2","OUTSIDER")){
                String project=Set.of("TL2","OUTSIDER").contains(alias)?"P2":"P1";
                db.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,?)",id(project),id(alias),alias.startsWith("TL")?"LEAD":"MEMBER");
            }
            task("T_A1","A","2026-11-20","IN_PROGRESS",true,"P1");
            task("T_A2","A","2026-11-22","TODO",true,"P1");
            task("T_B1","B","2026-11-17","IN_PROGRESS",true,"P1");
            task("T_B2","B","2026-11-19","IN_PROGRESS",true,"P1");
            task("T_C1","C","2026-11-13","IN_PROGRESS",true,"P1");
            task("T_D1","D","2026-12-01","IN_PROGRESS",true,"P1");
            task("T_E1","E","2026-11-20","TODO",false,"P1");
            task("T_DONE","A","2026-11-18","DONE",true,"P1");
            task("T_P2","OUTSIDER","2026-11-20","TODO",false,"P2");
            return fixture();
        }
        void task(String alias,String owner,String date,String status,boolean confirmed,String project){
            var deadline=LocalDate.parse(date).atTime(18,0).atZone(WeeklyPolicy.ZONE).toOffsetDateTime();
            db.update("""
                INSERT INTO tasks(id,project_id,assignee_id,title,description,expected_output,start_date,deadline,status,planning_status,confirmed_by,confirmed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,id(alias),id(project),id(owner),alias+" · test commitment","Scope "+alias,"Deliverable "+alias,LocalDate.of(2026,11,6),deadline,
                status,confirmed?"CONFIRMED":"DRAFT",confirmed?id(project.equals("P1")?"TL1":"TL2"):null,confirmed?weeks.timestamp():null);
        }
        public record ClockInput(String instant){}
        @PutMapping("/__test/clock") public Object time(@RequestBody ClockInput input){clock.set(OffsetDateTime.parse(input.instant()).toInstant().toString());return Map.of("now",weeks.timestamp());}
        public record JobInput(String job){}
        @PostMapping("/__test/jobs") public Object job(@RequestBody JobInput input){
            long before=db.queryForObject("SELECT count(*) FROM notifications",Long.class);
            var schedule=new CheckpointSchedule(jobs,weeks);
            if(input.job().equals("remind"))schedule.reminder();else if(input.job().equals("finalize"))schedule.finalizeCheckpoint();else throw new IllegalArgumentException("Unknown job");
            return Map.of("created",db.queryForObject("SELECT count(*) FROM notifications",Long.class)-before);
        }
    }
}
