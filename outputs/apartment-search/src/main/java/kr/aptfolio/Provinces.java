package kr.aptfolio;
import java.util.List;

/** Province labels and historical aliases. No network call is made during search. */
public final class Provinces {
 private Provinces() {}
 private static final List<List<String>> NAMES=List.of(
  List.of("전남광주통합특별시"),List.of("서울","서울특별시"),List.of("부산","부산광역시"),List.of("대구","대구광역시"),
  List.of("인천","인천광역시"),List.of("광주","광주광역시"),List.of("대전","대전광역시"),
  List.of("울산","울산광역시"),List.of("세종","세종특별자치시"),List.of("경기","경기도"),
  List.of("강원","강원특별자치도","강원도"),List.of("충북","충청북도"),List.of("충남","충청남도"),
  List.of("전북","전북특별자치도","전라북도"),List.of("전남","전라남도"),
  List.of("경북","경상북도"),List.of("경남","경상남도"),List.of("제주","제주특별자치도","제주도"));
 public static List<String> all() {return NAMES.stream().map(List::getFirst).toList();}
 public static List<String> aliases(String input) {
  return NAMES.stream().filter(n->n.contains(input)).findFirst()
   .orElseThrow(()->new IllegalArgumentException("시도 선택을 확인해주세요."));
 }
 public static String canonical(String input) {return input.isEmpty()?"":aliases(input).getFirst();}
}
