package kr.aptfolio.collection.jobs;

import kr.aptfolio.collection.molit.*;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/collection-jobs")
@ConditionalOnProperty(name="collection.api.enabled",havingValue="true")
public class CollectionJobApi {
 private final CollectionJobs jobs;
 private final String token,key;
 private final MolitClient client;
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"collection-worker");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public CollectionJobApi(CollectionJobs jobs,MolitClient client,@Value("${COLLECTION_ADMIN_TOKEN:}") String token,@Value("${MOLIT_SERVICE_KEY:}") String key){
  if(token.isBlank()||token.length()<32)throw new IllegalArgumentException("수집 API에는 32자 이상의 관리 토큰이 필요합니다.");
  this.jobs=jobs;this.client=client;this.token=token;this.key=key;
 }
 private void authorize(String supplied){if(!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
 private void configured(){if(key.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"수집 인증키 설정이 필요합니다.");}
 public record Request(String regionCode,String province,String district,String month){}
 @GetMapping public List<CollectionJobs.Job> list(@RequestHeader(value="X-Collection-Token",defaultValue="") String auth,@RequestParam(defaultValue="20") int limit){authorize(auth);jobs.recover();return jobs.list(limit);}
 @GetMapping("/{id}") public CollectionJobs.Job get(@RequestHeader(value="X-Collection-Token",defaultValue="") String auth,@PathVariable String id){authorize(auth);jobs.recover();return jobs.get(id);}
 @PostMapping public ResponseEntity<Map<String,String>> start(@RequestHeader(value="X-Collection-Token",defaultValue="") String auth,@RequestBody Request request){
  authorize(auth);configured();
  if(request==null||request.month()==null)throw new IllegalArgumentException("계약월 필요");
  var region=new MolitRegion(request.regionCode(),request.province(),request.district());
  var plan=MolitPlan.parse(region.code()+":"+region.province()+":"+region.district(),request.month(),"","");
  return submit(jobs.start(region,plan.months().getFirst()));
 }
 @PostMapping("/{id}/retry") public ResponseEntity<Map<String,String>> retry(@RequestHeader(value="X-Collection-Token",defaultValue="") String auth,@PathVariable String id){authorize(auth);configured();return submit(jobs.retry(id));}
 private ResponseEntity<Map<String,String>> submit(String id){
  try {worker.execute(()->{
   String failure="FETCH_FAILED";
   try {
    var job=jobs.get(id);var items=client.fetch(new MolitRegion(job.regionCode(),job.province(),job.district()),CollectionJobs.parseMonth(job.month()));
    failure="STORE_FAILED";jobs.complete(id,items);
   }catch(RuntimeException e){jobs.fail(id,failure);}
  });}catch(RejectedExecutionException e){jobs.fail(id,"EXECUTOR_BUSY");throw new ResponseStatusException(HttpStatus.CONFLICT,"수집기가 사용 중입니다. 실패 작업을 재시도하세요.");}
  return ResponseEntity.accepted().body(Map.of("id",id));
 }
 @ExceptionHandler(NoSuchElementException.class) ResponseEntity<Map<String,String>> missing(){return ResponseEntity.status(404).body(Map.of("error","not_found"));}
 @ExceptionHandler(IllegalStateException.class) ResponseEntity<Map<String,String>> conflict(){return ResponseEntity.status(409).body(Map.of("error","job_conflict"));}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,String>> invalid(){return ResponseEntity.badRequest().body(Map.of("error","invalid_request"));}
 @PreDestroy public void stop(){worker.shutdownNow();}
}
