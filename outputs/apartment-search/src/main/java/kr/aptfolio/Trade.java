package kr.aptfolio;
import java.math.BigDecimal;
import java.time.LocalDate;
public record Trade(long id, String region, String district, String apartment, String address,
 BigDecimal area, int floor, int builtYear, LocalDate dealDate, long price) {
 public String priceLabel() {
  long eok=price/10000, rest=price%10000;
  return eok==0 ? String.format("%,d만",rest) : eok+"억"+(rest==0 ? "" : " "+String.format("%,d만",rest));
 }
 public String key() {return region+"|"+district+"|"+apartment+"|"+address;}
}
