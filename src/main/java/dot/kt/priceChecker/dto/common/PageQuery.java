package dot.kt.priceChecker.dto.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageQuery(@Min(0) Integer page, @Min(1) @Max(200) Integer size) {
    public PageQuery {
        page = page == null ? 0 : page;
        size = size == null ? 50 : size;
    }
}
