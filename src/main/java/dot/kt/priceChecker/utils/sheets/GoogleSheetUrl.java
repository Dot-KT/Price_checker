package dot.kt.priceChecker.utils.sheets;

import dot.kt.priceChecker.exceptions.sheets.SheetAccessException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record GoogleSheetUrl(String spreadsheetId, Integer gid) {
    private static final Pattern PATH = Pattern.compile("/spreadsheets/(?:u/[0-9]{1,3}/)?d/([a-zA-Z0-9_-]{1,200})(?:/(?:edit|view|preview|htmlview))?/?");

    public static GoogleSheetUrl parse(String value) {
        if (value == null || value.isBlank() || value.length() > 2_048) {
            throw invalid("Provide a Google Sheets link.");
        }
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"docs.google.com".equalsIgnoreCase(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getPort() != -1) {
                throw invalid("Use an HTTPS docs.google.com/spreadsheets/d/... link.");
            }
            var path = PATH.matcher(uri.getRawPath());
            if (!path.matches()) {
                throw invalid("Use the spreadsheet's edit or view link, not a published or export link.");
            }
            Integer queryGid = readGid(uri.getRawQuery());
            Integer fragmentGid = readGid(uri.getRawFragment());
            if (queryGid != null && fragmentGid != null && !queryGid.equals(fragmentGid)) {
                throw invalid("The spreadsheet link contains conflicting gid parameters.");
            }
            return new GoogleSheetUrl(path.group(1), queryGid == null ? fragmentGid : queryGid);
        } catch (SheetAccessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid("The Google Sheets link is invalid.");
        }
    }

    public String canonicalSourceUrl(int selectedGid) {
        return "https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/edit#gid=" + selectedGid;
    }

    public String canonicalSourceUrl() {
        return "https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/edit"
                + (gid == null ? "" : "#gid=" + gid);
    }

    private static Integer readGid(String parameters) {
        if (parameters == null || parameters.isBlank()) return null;
        List<Integer> gids = new ArrayList<>();
        for (String parameter : parameters.split("&", -1)) {
            String[] pair = parameter.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (!key.equals("gid")) continue;
            if (pair.length != 2) throw invalid("The gid parameter must be a nonnegative integer.");
            String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            if (!value.matches("[0-9]{1,10}")) {
                throw invalid("The gid parameter must be a nonnegative integer.");
            }
            try {
                gids.add(Integer.valueOf(value));
                if (gids.size() > 1) throw invalid("The spreadsheet link contains duplicate gid parameters.");
            } catch (NumberFormatException exception) {
                throw invalid("The gid parameter is too large.");
            }
        }
        return gids.isEmpty() ? null : gids.getFirst();
    }

    private static SheetAccessException invalid(String message) {
        return new SheetAccessException(SheetAccessException.Kind.INVALID_URL, message);
    }
}
