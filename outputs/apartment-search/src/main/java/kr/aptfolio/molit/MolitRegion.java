package kr.aptfolio.molit;

/** Explicit API region code and display labels; labels are operator supplied. */
public record MolitRegion(String code, String province, String district) {
 public static final MolitRegion JONGNO = new MolitRegion("11110", "서울", "종로구");
 public MolitRegion {
  if (code == null || !code.matches("[0-9]{5}") || province == null || district == null
      || !province.matches("[가-힣A-Za-z ]{1,30}") || !district.matches("[가-힣A-Za-z ]{1,50}")
      || province.isBlank() || district.isBlank())
   throw new IllegalArgumentException("지역은 5자리 코드:시도명:시군구명 형식이어야 합니다.");
  province = province.trim(); district = district.trim();
 }
 public String addressPrefix() { return province + " " + district + " "; }
}
