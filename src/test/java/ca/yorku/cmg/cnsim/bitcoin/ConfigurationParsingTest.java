package ca.yorku.cmg.cnsim.bitcoin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for configuration parsing functionality.
 * <p>
 * This test verifies that:
 * <ul>
 *     <li>Hashpower change configuration strings are parsed correctly</li>
 *     <li>Invalid formats are rejected with appropriate error messages</li>
 *     <li>Edge cases (empty, null, malformed) are handled properly</li>
 * </ul>
 * </p>
 */
public class ConfigurationParsingTest {

    /**
     * Helper method to invoke the private parseHashPowerChanges method via reflection.
     */
    private Object[] parseHashPowerChanges(String input) throws Exception {
        BitcoinSimulatorFactory factory = new BitcoinSimulatorFactory();
        Method method = BitcoinSimulatorFactory.class.getDeclaredMethod("parseHashPowerChanges", String.class);
        method.setAccessible(true);
        return (Object[]) method.invoke(factory, input);
    }

    @Test
    public void testParseHashPowerChanges_EmptyString() throws Exception {
        Object[] result = parseHashPowerChanges("");
        assertEquals(0, result.length, "Empty string should return empty array");
    }

    @Test
    public void testParseHashPowerChanges_EmptyBraces() throws Exception {
        Object[] result = parseHashPowerChanges("{}");
        assertEquals(0, result.length, "Empty braces should return empty array");
    }

    @Test
    public void testParseHashPowerChanges_Null() throws Exception {
        Object[] result = parseHashPowerChanges(null);
        assertEquals(0, result.length, "Null should return empty array");
    }

    @Test
    public void testParseHashPowerChanges_SingleEntry() throws Exception {
        Object[] result = parseHashPowerChanges("{0:5.0E10:10000}");
        assertEquals(1, result.length, "Single entry should parse to one element");
    }

    @Test
    public void testParseHashPowerChanges_MultipleEntries() throws Exception {
        Object[] result = parseHashPowerChanges("{0:5.0E10:10000, 1:3.0E10:20000, 2:7.0E10:30000}");
        assertEquals(3, result.length, "Three entries should parse to three elements");
    }

    @Test
    public void testParseHashPowerChanges_WithWhitespace() throws Exception {
        Object[] result = parseHashPowerChanges("{ 0 : 5.0E10 : 10000 , 1 : 3.0E10 : 20000 }");
        assertEquals(2, result.length, "Should handle whitespace correctly");
    }

    @Test
    public void testParseHashPowerChanges_MissingOpeningBracket() {
        Exception exception = assertThrows(Exception.class, () -> {
            parseHashPowerChanges("0:5.0E10:10000}");
        });
        assertTrue(exception.getCause().getMessage().contains("missing opening bracket"),
                "Should report missing opening bracket");
    }

    @Test
    public void testParseHashPowerChanges_MissingClosingBracket() {
        Exception exception = assertThrows(Exception.class, () -> {
            parseHashPowerChanges("{0:5.0E10:10000");
        });
        assertTrue(exception.getCause().getMessage().contains("missing closing bracket"),
                "Should report missing closing bracket");
    }

    @Test
    public void testParseHashPowerChanges_InvalidFormat_TooFewParts() {
        Exception exception = assertThrows(Exception.class, () -> {
            parseHashPowerChanges("{0:5.0E10}");
        });
        assertTrue(exception.getCause().getMessage().contains("must have format"),
                "Should report invalid format");
    }

    @Test
    public void testParseHashPowerChanges_InvalidFormat_TooManyParts() {
        Exception exception = assertThrows(Exception.class, () -> {
            parseHashPowerChanges("{0:5.0E10:10000:extra}");
        });
        assertTrue(exception.getCause().getMessage().contains("must have format"),
                "Should report invalid format");
    }

    @Test
    public void testParseHashPowerChanges_InvalidNodeID() {
        Exception exception = assertThrows(Exception.class, () -> {
            parseHashPowerChanges("{notanumber:5.0E10:10000}");
        });
        assertTrue(exception.getCause().getMessage().contains("invalid number format"),
                "Should report invalid node ID format");
    }

    @Test
    public void testParseHashPowerChanges_InvalidHashPower() {
        Exception exception = assertThrows(Exception.class, () -> {
            parseHashPowerChanges("{0:notanumber:10000}");
        });
        assertTrue(exception.getCause().getMessage().contains("invalid number format"),
                "Should report invalid hashpower format");
    }

    @Test
    public void testParseHashPowerChanges_InvalidTime() {
        Exception exception = assertThrows(Exception.class, () -> {
            parseHashPowerChanges("{0:5.0E10:notanumber}");
        });
        assertTrue(exception.getCause().getMessage().contains("invalid number format"),
                "Should report invalid time format");
    }

    @Test
    public void testParseHashPowerChanges_NegativeHashPower() {
        Exception exception = assertThrows(Exception.class, () -> {
            parseHashPowerChanges("{0:-5.0E10:10000}");
        });
        assertTrue(exception.getCause().getMessage().contains("cannot be negative"),
                "Should reject negative hashpower");
    }

    @Test
    public void testParseHashPowerChanges_NegativeTime() {
        Exception exception = assertThrows(Exception.class, () -> {
            parseHashPowerChanges("{0:5.0E10:-10000}");
        });
        assertTrue(exception.getCause().getMessage().contains("cannot be negative"),
                "Should reject negative time");
    }

    @Test
    public void testParseHashPowerChanges_ZeroHashPower() throws Exception {
        Object[] result = parseHashPowerChanges("{0:0.0:10000}");
        assertEquals(1, result.length, "Zero hashpower should be valid");
    }

    @Test
    public void testParseHashPowerChanges_ZeroTime() throws Exception {
        Object[] result = parseHashPowerChanges("{0:5.0E10:0}");
        assertEquals(1, result.length, "Zero time should be valid");
    }

    @Test
    public void testParseHashPowerChanges_LargeValues() throws Exception {
        Object[] result = parseHashPowerChanges("{999:9.99E99:9999999999}");
        assertEquals(1, result.length, "Should handle large values");
    }

    @Test
    public void testParseHashPowerChanges_ScientificNotation() throws Exception {
        Object[] result = parseHashPowerChanges("{0:5.0E10:10000, 1:3.5E9:20000}");
        assertEquals(2, result.length, "Should handle scientific notation");
    }

    @Test
    public void testParseHashPowerChanges_DecimalHashPower() throws Exception {
        Object[] result = parseHashPowerChanges("{0:123.456:10000}");
        assertEquals(1, result.length, "Should handle decimal hashpower values");
    }
}
