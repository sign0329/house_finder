package kr.aptfolio.molit;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MolitPlanTest {
 @Test void expandsMultipleRegionsAndInclusiveYearBoundary() {
  var plan=MolitPlan.parse("11110:서울:종로구,11680:서울:강남구", "202609", "202512", "202602");
  assertEquals(List.of("11110","11680"),plan.regions().stream().map(MolitRegion::code).toList());
  assertEquals(List.of(YearMonth.of(2025,12),YearMonth.of(2026,1),YearMonth.of(2026,2)),plan.months());
 }
 @Test void preservesLegacySingleMonthDefault() {
  var plan=MolitPlan.parse("11110:서울:종로구","202609","","");
  assertEquals(List.of(MolitRegion.JONGNO),plan.regions());
  assertEquals(List.of(YearMonth.of(2026,9)),plan.months());
 }
 @Test void rejectsInvalidAndUnboundedPlansBeforeCollection() {
  for(String[] range:List.of(new String[]{"202601",""},new String[]{"","202601"},new String[]{"202602","202601"},new String[]{"202513","202601"},new String[]{"200501","200502"},new String[]{"209901","209902"},new String[]{"202401","202601"}))
   assertThrows(IllegalArgumentException.class,()->MolitPlan.parse("11110:서울:종로구","202609",range[0],range[1]));
  for(String regions:List.of("", "1111:서울:종로구","11110:서울", "11110:서울:종로구,11110:서울:다른구","11110:서울:종로구,"))
   assertThrows(IllegalArgumentException.class,()->MolitPlan.parse(regions,"202609","",""));
 }
}
