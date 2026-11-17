package kr.aptfolio.account;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:profile-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class AccountProfileTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void users() {
        db.update(
                "insert into user_account(username,nickname,password_hash) values('photoalice','사진"
                        + " 사용자','unused'),('photobob','다른 사용자','unused')");
    }

    MockMultipartFile photo() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("photo", "image.png", "image/png", bytes.toByteArray());
    }

    @Test
    void photoIsNormalizedPersistedAndOnlyVisibleToItsOwner() throws Exception {
        mvc.perform(multipart("/account/photo").file(photo()).with(user("photoalice")).with(csrf()))
                .andExpect(redirectedUrl("/account?photoUpdated"));
        var response =
                mvc.perform(get("/account/photo").with(user("photoalice")))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType("image/png"))
                        .andExpect(header().string("Cache-Control", containsString("no-store")))
                        .andReturn();
        var image =
                ImageIO.read(
                        new java.io.ByteArrayInputStream(
                                response.getResponse().getContentAsByteArray()));
        assertEquals(256, image.getWidth());
        assertEquals(128, image.getHeight());
        mvc.perform(get("/account/photo").with(user("photobob"))).andExpect(status().isNotFound());
        mvc.perform(get("/account/photo")).andExpect(status().is3xxRedirection());
    }

    @Test
    void invalidOversizedAndCsrfMissingUploadsAreRejected() throws Exception {
        mvc.perform(
                        multipart("/account/photo")
                                .file(
                                        new MockMultipartFile(
                                                "photo",
                                                "fake.png",
                                                "image/png",
                                                "not an image".getBytes()))
                                .with(user("photoalice"))
                                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("사진")));
        mvc.perform(
                        multipart("/account/photo")
                                .file(
                                        new MockMultipartFile(
                                                "photo",
                                                "large.png",
                                                "image/png",
                                                new byte[2 * 1024 * 1024 + 1]))
                                .with(user("photoalice"))
                                .with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/account/photo").file(photo()).with(user("photoalice")))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountListsOnlyOwnedBookmarksAndExplainsEmptyList() throws Exception {
        db.update(
                "insert into user_bookmark(user_id,apartment_key,region,district,apartment,address)"
                    + " select id,'key-alice','서울','종로구','나의 관심 아파트','주소' from user_account where"
                    + " username='photoalice'");
        mvc.perform(get("/account").with(user("photoalice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("나의 관심 아파트")))
                .andExpect(content().string(containsString("현재 조회 가능한 거래가 없습니다")));
        mvc.perform(get("/account").with(user("photobob")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("나의 관심 아파트"))))
                .andExpect(content().string(containsString("아직 저장한 아파트가 없어요")));
    }

    @Test
    void photoCanBeDeletedWithoutChangingAnotherAccount() throws Exception {
        mvc.perform(
                multipart("/account/photo").file(photo()).with(user("photoalice")).with(csrf()));
        mvc.perform(post("/account/photo/delete").with(user("photobob")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/account/photo").with(user("photoalice"))).andExpect(status().isOk());
        mvc.perform(post("/account/photo/delete").with(user("photoalice")).with(csrf()))
                .andExpect(redirectedUrl("/account?photoDeleted"));
        mvc.perform(get("/account/photo").with(user("photoalice")))
                .andExpect(status().isNotFound());
    }
}
