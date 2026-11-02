package kr.aptfolio.collection.jobs;

import kr.aptfolio.collection.molit.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All writers acquire the existing collection mutex first, including publication. */
@Service
public class CollectionJobs {
 private final JdbcTemplate db;
 private final MolitStore store;
 public CollectionJobs(JdbcTemplate db,MolitStore store){this.db=db;this.store=store;}
 public record Job(String id,String regionCode,String province,String district,String month,String status,int attempt,int saved,int cancelled,String failureType,LocalDateTime startedAt,LocalDateTime finishedAt){}
 private final org.springframework.jdbc.core.RowMapper<Job> row=(r,n)->new Job(r.getString("id"),r.getString("region_code"),r.getString("province"),r.getString("district"),r.getString("deal_month"),r.getString("status"),r.getInt("attempt"),r.getInt("saved"),r.getInt("cancelled"),r.getString("failure_type"),r.getTimestamp("started_at").toLocalDateTime(),r.getTimestamp("finished_at")==null?null:r.getTimestamp("finished_at").toLocalDateTime());
 private void lock(){db.queryForObject("select id from molit_import_state where id=1 for update",Integer.class);}
 private void expire(){db.update("update collection_job set status='INTERRUPTED',failure_type='LEASE_EXPIRED',finished_at=CURRENT_TIMESTAMP where status='RUNNING' and lease_until<CURRENT_TIMESTAMP");}
 @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
 @Transactional public void onStartup(){recover();}
 @Transactional public void recover(){lock();expire();}
 @Transactional public String start(MolitRegion region,YearMonth month){lock();expire();return claim(region,month);}
 private String claim(MolitRegion region,YearMonth month){
  String ym=month.toString().replace("-","");
  MolitPlan.parse(region.code()+":"+region.province()+":"+region.district(),ym,"","");
  if(db.queryForObject("select count(*) from collection_job where region_code=? and deal_month=? and status='RUNNING'",Integer.class,region.code(),ym)>0)throw new IllegalStateException("이미 실행 중인 구간입니다.");
  int attempt=db.queryForObject("select coalesce(max(attempt),0)+1 from collection_job where region_code=? and deal_month=?",Integer.class,region.code(),ym);
  String id=UUID.randomUUID().toString();
  db.update("insert into collection_job(id,region_code,province,district,deal_month,status,attempt,lease_until) values(?,?,?,?,?,'RUNNING',?,CURRENT_TIMESTAMP + INTERVAL '30' MINUTE)",id,region.code(),region.province(),region.district(),ym,attempt);
  return id;
 }
 @Transactional public String retry(String id){
  lock();expire();Job old=get(id);
  if(!Set.of("FAILED","INTERRUPTED").contains(old.status()))throw new IllegalStateException("실패·중단 작업만 재시도할 수 있습니다.");
  // Only the latest attempt may be retried; stale failed IDs cannot create repeat work.
  int latest=db.queryForObject("select max(attempt) from collection_job where region_code=? and deal_month=?",Integer.class,old.regionCode(),old.month());
  if(old.attempt()!=latest)throw new IllegalStateException("가장 최근 작업을 사용하세요.");
  return claim(new MolitRegion(old.regionCode(),old.province(),old.district()),parseMonth(old.month()));
 }
 public static YearMonth parseMonth(String month){return YearMonth.parse(month,java.time.format.DateTimeFormatter.ofPattern("uuuuMM"));}
 @Transactional public int complete(String id,List<MolitXml.Item> items){
  lock();Job job=get(id);
  if(!job.status().equals("RUNNING")||db.queryForObject("select count(*) from collection_job where id=? and lease_until>=CURRENT_TIMESTAMP",Integer.class,id)==0)throw new IllegalStateException("작업 유효 시간이 만료되었습니다.");
  int count=store.replace(new MolitRegion(job.regionCode(),job.province(),job.district()),parseMonth(job.month()),items);
  db.update("update collection_job set status='SUCCEEDED',saved=?,cancelled=?,finished_at=CURRENT_TIMESTAMP where id=?",count,items.size()-count,id);
  return count;
 }
 @Transactional public void fail(String id,String type){
  if(!Set.of("FETCH_FAILED","STORE_FAILED","EXECUTOR_BUSY").contains(type))throw new IllegalArgumentException("실패 유형 오류");
  lock();db.update("update collection_job set status='FAILED',failure_type=?,finished_at=CURRENT_TIMESTAMP where id=? and status='RUNNING'",type,id);
 }
 public Job get(String id){return db.query("select * from collection_job where id=?",row,id).stream().findFirst().orElseThrow(()->new NoSuchElementException("작업이 없습니다."));}
 public List<Job> list(int limit){if(limit<1||limit>100)throw new IllegalArgumentException("limit는 1~100");return db.query("select * from collection_job order by started_at desc,id desc limit ?",row,limit);}
}
