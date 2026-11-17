package kr.aptfolio.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestLimitTest {
    @Test
    void clientLimitResetsAndOtherClientsRemainUsable() {
        var f = new WebSecurityFilter(1, 3);
        assertThat(f.allow("a", 0)).isTrue();
        assertThat(f.allow("a", 100)).isFalse();
        assertThat(f.allow("b", 100)).isTrue();
        assertThat(f.allow("a", 60000)).isTrue();
    }

    @Test
    void globalLimitBoundsDistributedTraffic() {
        var f = new WebSecurityFilter(2, 2);
        assertThat(f.allow("a", 0)).isTrue();
        assertThat(f.allow("b", 0)).isTrue();
        assertThat(f.allow("c", 0)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void loginLimitResetsOnlyAtNextMinute() {
        var filter = new WebSecurityFilter(120, 6000);
        for (int i = 0; i < 10; i++)
            org.junit.jupiter.api.Assertions.assertTrue(filter.allowLogin("client", 59999));
        org.junit.jupiter.api.Assertions.assertFalse(filter.allowLogin("client", 59999));
        org.junit.jupiter.api.Assertions.assertTrue(filter.allowLogin("client", 60000));
    }
}
