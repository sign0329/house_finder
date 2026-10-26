package kr.aptfolio;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class RequestLimitTest {
 @Test void clientLimitResetsAndOtherClientsRemainUsable(){
  var f=new WebSecurityFilter(1,3);
  assertThat(f.allow("a",0)).isTrue();
  assertThat(f.allow("a",100)).isFalse();
  assertThat(f.allow("b",100)).isTrue();
  assertThat(f.allow("a",60000)).isTrue();
 }
 @Test void globalLimitBoundsDistributedTraffic(){
  var f=new WebSecurityFilter(2,2);
  assertThat(f.allow("a",0)).isTrue();
  assertThat(f.allow("b",0)).isTrue();
  assertThat(f.allow("c",0)).isFalse();
 }
}
