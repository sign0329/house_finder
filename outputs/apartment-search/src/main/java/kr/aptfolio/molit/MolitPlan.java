package kr.aptfolio.molit;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record MolitPlan(List<MolitRegion> regions, List<YearMonth> months) {
 public MolitPlan { regions=List.copyOf(regions); months=List.copyOf(months); }
 public static MolitPlan parse(String regionSpec, String month, String from, String to) {
  var regions=new ArrayList<MolitRegion>();
  var codes=new HashSet<String>();
  for(String entry:regionSpec.split(",",-1)) {
   String[] parts=entry.trim().split(":",-1);
   if(parts.length!=3)throw new IllegalArgumentException("molit.regions는 코드:시도명:시군구명 목록이어야 합니다.");
   var region=new MolitRegion(parts[0].trim(),parts[1].trim(),parts[2].trim());
   if(!codes.add(region.code()))throw new IllegalArgumentException("중복 지역 코드는 허용하지 않습니다.");
   regions.add(region);
  }
  if(regions.size()>10)throw new IllegalArgumentException("한 번에 최대 10개 지역을 지정하세요.");
  boolean range=!from.isBlank()||!to.isBlank();
  YearMonth start=parseMonth(range?from:month),end=parseMonth(range?to:month);
  long length=ChronoUnit.MONTHS.between(start,end)+1;
  if(length<1||length>12)throw new IllegalArgumentException("수집 기간은 시작 월부터 최대 12개월이어야 합니다.");
  var months=new ArrayList<YearMonth>();
  for(int i=0;i<length;i++)months.add(start.plusMonths(i));
  return new MolitPlan(regions,months);
 }
 private static YearMonth parseMonth(String text) {
  try {
   if(!text.matches("[0-9]{6}"))throw new IllegalArgumentException();
   var month=YearMonth.parse(text,DateTimeFormatter.ofPattern("uuuuMM").withResolverStyle(ResolverStyle.STRICT));
   if(month.isBefore(YearMonth.of(2006,1))||month.isAfter(YearMonth.now()))throw new IllegalArgumentException();
   return month;
  } catch(Exception e) { throw new IllegalArgumentException("계약월은 200601부터 현재 월 사이의 YYYYMM이어야 합니다. 기간의 양 끝을 지정하세요."); }
 }
}
