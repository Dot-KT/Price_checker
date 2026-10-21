package dot.kt.priceChecker.utils.sheets;

import dot.kt.priceChecker.exceptions.sheets.SheetAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleSheetUrlTest {
    @Test
    void extractsSpreadsheetAndGidFromStandardLinks() {
        var query = GoogleSheetUrl.parse("https://docs.google.com/spreadsheets/d/abc_123-X/edit?usp=sharing&gid=42");
        var fragment = GoogleSheetUrl.parse("https://docs.google.com/spreadsheets/d/abc_123-X/view#gid=0");
        assertThat(query.spreadsheetId()).isEqualTo("abc_123-X");
        assertThat(query.gid()).isEqualTo(42);
        assertThat(fragment.gid()).isZero();
        assertThat(GoogleSheetUrl.parse("https://docs.google.com/spreadsheets/d/id/edit?gid=0#gid=0").gid()).isZero();
        assertThat(query.canonicalSourceUrl(42)).isEqualTo("https://docs.google.com/spreadsheets/d/abc_123-X/edit#gid=42");
    }

    @Test
    void noGidSelectsFirstTabAndCanonicalizationDiscardsOtherParameters() {
        var source = GoogleSheetUrl.parse(" https://docs.google.com/spreadsheets/d/abc_123-X/?usp=sharing ");
        assertThat(source.gid()).isNull();
        assertThat(source.canonicalSourceUrl(9)).endsWith("/edit#gid=9");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://docs.google.com/spreadsheets/d/id/edit",
            "https://evil.example/spreadsheets/d/id/edit",
            "https://docs.google.com.evil.example/spreadsheets/d/id/edit",
            "https://docs.google.com@127.0.0.1/spreadsheets/d/id/edit",
            "https://user@docs.google.com/spreadsheets/d/id/edit",
            "https://docs.google.com:443/spreadsheets/d/id/edit",
            "https://docs.google.com/spreadsheets/d/id/export?format=csv",
            "https://docs.google.com/spreadsheets/d/e/published-id/pubhtml",
            "https://docs.google.com/spreadsheets/d/id%2Fanother/edit",
            "https://docs.google.com/spreadsheets/d/id/edit#gid=-1",
            "https://docs.google.com/spreadsheets/d/id/edit#gid=1.2",
            "https://docs.google.com/spreadsheets/d/id/edit#gid=2147483648",
            "https://docs.google.com/spreadsheets/d/id/edit?gid=",
            "https://docs.google.com/spreadsheets/d/id/edit?gid=1#gid=2",
            "https://docs.google.com/spreadsheets/d/id/edit?gid=1&gid=1",
            "https://docs.google.com/spreadsheets/d/id/edit#gid=NaN",
            "file:///etc/passwd", "not a url", ""
    })
    void rejectsNonGoogleAndAmbiguousOrMalformedLinks(String value) {
        assertThatThrownBy(() -> GoogleSheetUrl.parse(value))
                .isInstanceOfSatisfying(SheetAccessException.class,
                        error -> assertThat(error.getKind()).isEqualTo(SheetAccessException.Kind.INVALID_URL));
    }
}
