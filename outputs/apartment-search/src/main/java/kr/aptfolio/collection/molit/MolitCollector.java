package kr.aptfolio.collection.molit;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Each region/month is fetched completely before its own store transaction. */
public class MolitCollector {
 public record Result(String regionCode, YearMonth month, boolean success, int saved, int cancelled) {}
 private final kr.aptfolio.collection.jobs.CollectionJobs jobs;
 private final MolitClient client;
 private final MolitStore store;
 public MolitCollector(MolitClient client,MolitStore store) { this(client,store,null); }
 public MolitCollector(MolitClient client,MolitStore store,kr.aptfolio.collection.jobs.CollectionJobs jobs) { this.client=client;this.store=store;this.jobs=jobs; }
 public List<Result> collect(MolitPlan plan, Consumer<Result> report) {
  var results=new ArrayList<Result>();
  for(var region:plan.regions())for(var month:plan.months()) {
   if(Thread.currentThread().isInterrupted())throw new IllegalStateException("수집이 중단되었습니다. 완료되지 않은 구간을 다시 실행하세요.");
   Result result;
   String jobId=null;String failureType="FETCH_FAILED";
   try {
    if(jobs!=null)jobId=jobs.start(region,month);
    var items=client.fetch(region,month);
    failureType="STORE_FAILED";
    int saved=jobs==null?store.replace(region,month,items):jobs.complete(jobId,items);
    result=new Result(region.code(),month,true,saved,items.size()-saved);
   } catch(RuntimeException e) {
    if(jobId!=null)jobs.fail(jobId,failureType);
    // Never log upstream URLs, response bodies, credentials, or SQL exception messages.
    result=new Result(region.code(),month,false,0,0);
   }
   results.add(result);report.accept(result);
  }
  return List.copyOf(results);
 }
}
