package com.example.demo.config;

import com.example.demo.service.WeeklyPolicy;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Profile("demo")
@Component
public class DemoSeed implements ApplicationRunner {
    public static UUID id(String name) { return UUID.nameUUIDFromBytes(("hanare-demo:"+name).getBytes(StandardCharsets.UTF_8)); }
    private final JdbcTemplate db;
    private final Environment env;
    private final PasswordEncoder passwords;
    private final WeeklyPolicy weeks;
    public DemoSeed(JdbcTemplate db,Environment env,PasswordEncoder passwords,WeeklyPolicy weeks) { this.db=db;this.env=env;this.passwords=passwords;this.weeks=weeks; }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        String password=env.getProperty("hanare.demo.password",env.getProperty("HANARE_DEMO_PASSWORD",""));
        if(password.length()<12) throw new IllegalStateException("Demo seed requires HANARE_DEMO_PASSWORD with at least 12 characters.");
        var lead=id("lead");var project=id("project");var friday=weeks.currentFriday();var hash=passwords.encode(password);
        db.update("INSERT INTO users(id,name,email,password_hash) VALUES (?,?,?,?) ON CONFLICT (id) DO NOTHING",lead,"Minh · Team lead","lead@hanare.demo",hash);
        db.update("INSERT INTO projects(id,name,deadline) VALUES (?,?,?) ON CONFLICT (id) DO NOTHING",project,"Hanare · Student Team",friday.plusWeeks(3).atTime(23,59).atZone(WeeklyPolicy.ZONE).toOffsetDateTime());
        db.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,'LEAD') ON CONFLICT DO NOTHING",project,lead);
        String[] names={"An Nguyen","Binh Tran","Chi Le","Dung Pham","Giang Ho","Linh Vu"};
        for(int i=1;i<=6;i++) {
            var user=id("member"+i);
            db.update("INSERT INTO users(id,name,email,password_hash) VALUES (?,?,?,?) ON CONFLICT (id) DO NOTHING",user,names[i-1],"member"+i+"@hanare.demo",hash);
            db.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,'MEMBER') ON CONFLICT DO NOTHING",project,user);
        }
        task("screen",project,lead,1,"Build the task detail screen","An accessible task page with plan confirmation.",friday,"IN_PROGRESS","CONFIRMED");
        task("api",project,lead,2,"Implement project API","Documented project endpoints and permission checks.",friday.plusWeeks(2),"IN_PROGRESS","CONFIRMED");
        task("report-a",project,lead,3,"Prepare checkpoint copy","Clear messages for submitted and missing reports.",friday,"IN_PROGRESS","CONFIRMED");
        task("report-b",project,lead,3,"Review mobile layout","Responsive dashboard at 375px.",friday.plusDays(2),"TODO","CONFIRMED");
        task("overdue",project,lead,4,"Finish integration notes","A shared integration document for Team B.",friday.minusWeeks(1),"IN_PROGRESS","CONFIRMED");
        task("completed",project,lead,5,"Define project vocabulary","A glossary agreed with the team.",friday,"DONE","CONFIRMED");
        task("draft",project,lead,1,"Propose next week's scope","A realistic deliverable and a single timeline.",friday.plusWeeks(1),"TODO","DRAFT");
        report("report-a",friday,"ACHIEVED","Copy draft is ready for discussion.");
        report("report-b",friday,"NOT_ACHIEVED","Still checking the narrow table layout; actual deadline is Sunday.");
    }
    private void task(String key,UUID project,UUID lead,int member,String title,String output,LocalDate deadline,String status,String planning) {
        var confirmed=planning.equals("CONFIRMED");
        db.update("""
            INSERT INTO tasks(id,project_id,assignee_id,title,description,expected_output,start_date,deadline,status,planning_status,confirmed_by,confirmed_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING
            """,id(key),project,id("member"+member),title,"Demo task. Discuss scope with your TL before confirmation.",output,
            deadline.minusDays(4),deadline.atTime(23,59).atZone(WeeklyPolicy.ZONE).toOffsetDateTime(),status,planning,confirmed?lead:null,confirmed?weeks.timestamp():null);
    }
    private void report(String key,LocalDate friday,String result,String note) {
        db.update("INSERT INTO task_reports(id,task_id,checkpoint_date,result,note) VALUES (?,?,?,?,?) ON CONFLICT (task_id,checkpoint_date) DO NOTHING",id("report:"+key+":"+friday),id(key),friday,result,note);
    }
}
