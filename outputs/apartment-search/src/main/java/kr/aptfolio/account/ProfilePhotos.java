package kr.aptfolio.account;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

@Service
public class ProfilePhotos {
    private final NamedParameterJdbcTemplate db;

    public ProfilePhotos(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public boolean exists(String username) {
        return Boolean.TRUE.equals(
                db.queryForObject(
                        "select profile_photo is not null from user_account where"
                                + " username=:username",
                        Map.of("username", username),
                        Boolean.class));
    }

    public byte[] get(String username) {
        return db.queryForObject(
                "select profile_photo from user_account where username=:username",
                Map.of("username", username),
                (r, n) -> r.getBytes(1));
    }

    public void delete(String username) {
        db.update(
                "update user_account set profile_photo=null where username=:username",
                Map.of("username", username));
    }

    public void save(String username, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
            throw new IllegalArgumentException("사진은 2MB 이하의 PNG 또는 JPEG 파일을 선택해주세요.");
        byte[] normalized;
        try (var input = new MemoryCacheImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
                throw new IllegalArgumentException("사진 파일을 읽을 수 없습니다. PNG 또는 JPEG를 선택해주세요.");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
                    throw new IllegalArgumentException("사진은 PNG 또는 JPEG만 지원합니다.");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1
                        || height < 1
                        || width > 4096
                        || height > 4096
                        || (long) width * height > 8_000_000)
                    throw new IllegalArgumentException(
                            "사진은 가로·세로 4096px 이하, 총 800만 화소 이하로 선택해주세요.");
                BufferedImage original = reader.read(0);
                double scale = Math.min(1.0, 256.0 / Math.max(width, height));
                BufferedImage thumbnail =
                        new BufferedImage(
                                Math.max(1, (int) Math.round(width * scale)),
                                Math.max(1, (int) Math.round(height * scale)),
                                BufferedImage.TYPE_INT_ARGB);
                var graphics = thumbnail.createGraphics();
                try {
                    graphics.setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(
                            original, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), null);
                } finally {
                    graphics.dispose();
                }
                var output = new ByteArrayOutputStream();
                ImageIO.write(thumbnail, "png", output);
                normalized = output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("사진을 처리하지 못했습니다. 다른 PNG 또는 JPEG 파일을 선택해주세요.");
        }
        db.update(
                "update user_account set profile_photo=:photo where username=:username",
                Map.of("username", username, "photo", normalized));
    }
}
