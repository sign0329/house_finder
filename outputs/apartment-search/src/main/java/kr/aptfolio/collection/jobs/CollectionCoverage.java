package kr.aptfolio.collection.jobs;

import java.time.LocalDateTime;
import java.util.*;
import kr.aptfolio.collection.molit.MolitPlan;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Counts and job history share a repeatable-read snapshot. No upstream calls. */
@Service
public class CollectionCoverage {
 private final NamedParameterJdbcTemplate db;
 public CollectionCoverage(NamedParameterJdbcTemplate db) { this.db = db; }
 public record Month(String regionCode, String month, long tradeCount, String status,
                     String jobId, LocalDateTime lastSucceededAt) {}
 @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
 public List<Month> list(String regionCode, String from, String to) {
  if(regionCode == null || !regionCode.matches("[0-9]{5}") || from == null || to == null || from.isBlank() || to.isBlank())
   throw new IllegalArgumentException("지역 코드와 시작·종료 계약월이 필요합니다.");
  var plan = MolitPlan.parse(regionCode + ":조회:조회", "", from, to);
  var params = Map.of("region", regionCode, "from", from, "to", to);
  var counts = new HashMap<String,Long>();
  db.query("select source_month,count(*) as total from trade where source='MOLIT' and source_region=:region and source_month between :from and :to group by source_month",
   params, (org.springframework.jdbc.core.RowCallbackHandler) r -> counts.put(r.getString("source_month"), r.getLong("total")));
  record History(String status, String id, LocalDateTime success) {}
  var history = new HashMap<String,History>();
  db.query("""
   select * from (
    select deal_month,status,id,
     max(case when status='SUCCEEDED' then finished_at end) over(partition by deal_month) as last_success,
     row_number() over(partition by deal_month order by attempt desc,started_at desc,id desc) as rank
    from collection_job where region_code=:region and deal_month between :from and :to
   ) ranked where rank=1
   """, params, (org.springframework.jdbc.core.RowCallbackHandler) r -> {
    var success = r.getTimestamp("last_success");
    history.put(r.getString("deal_month"), new History(r.getString("status"),r.getString("id"),success==null?null:success.toLocalDateTime()));
   });
  return plan.months().stream().map(month -> {
   String ym = month.toString().replace("-", "");
   long count = counts.getOrDefault(ym,0L); var job = history.get(ym);
   return new Month(regionCode,ym,count,job==null?(count>0?"DATA_WITHOUT_HISTORY":"NOT_COLLECTED"):job.status(),job==null?null:job.id(),job==null?null:job.success());
  }).toList();
 }
}
