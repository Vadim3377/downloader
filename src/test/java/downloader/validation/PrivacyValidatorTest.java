package downloader.validation;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyValidatorTest {
    @Test
    void detectsRepresentativePrivacyRisks() {
        String text = "user=test@example.com ip=192.168.1.10 path=C:\\Users\\Vadim\\project token=api_key=abcdef1234567890 id=123456789012";

        Set<PrivacyFindingType> types = new PrivacyValidator().scan(text).stream()
                .map(PrivacyFinding::type)
                .collect(Collectors.toSet());

        assertTrue(types.contains(PrivacyFindingType.EMAIL_ADDRESS));
        assertTrue(types.contains(PrivacyFindingType.IP_ADDRESS));
        assertTrue(types.contains(PrivacyFindingType.LOCAL_USER_PATH));
        assertTrue(types.contains(PrivacyFindingType.API_KEY_LIKE_SECRET));
        assertTrue(types.contains(PrivacyFindingType.LONG_NUMERIC_IDENTIFIER));
    }
}
