package com.byd.aebtoggle.core;

final class ParcelParserTest {
    private ParcelParserTest() {
    }

    static void run() {
        T.section("ParcelParser: replies that must be understood");
        T.eq("OFF", Aeb.OFF, ParcelParser.parseRead("Result: Parcel(00000000 00000000   '........')"));
        T.eq("ON", Aeb.ON, ParcelParser.parseRead("Result: Parcel(00000000 00000001   '........')"));
        T.eq("hex letters are parsed (0x0A is neither 0 nor 1)", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel(00000000 0000000A   '........')"));
        T.eq("lower case hex letters are parsed too", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel(00000000 0000000a   '........')"));
        T.eq("CRLF and blank lines around", Aeb.OFF,
                ParcelParser.parseRead("\r\n\r\nResult: Parcel(00000000 00000000   '........')\r\n\r\n"));
        T.eq("text before the result line is ignored", Aeb.ON,
                ParcelParser.parseRead("WARNING: something\nResult: Parcel(00000000 00000001   '........')\n"));
        T.eq("dump containing parentheses (the last ')' closes the parcel)", Aeb.ON,
                ParcelParser.parseRead("Result: Parcel(00000000 00000001   '..(.)...')"));
        T.eq("dump with a space inside", Aeb.OFF,
                ParcelParser.parseRead("Result: Parcel(00000000 00000000   '.... ....')"));
        T.eq("no dump at all", Aeb.ON, ParcelParser.parseRead("Result: Parcel(00000000 00000001)"));

        T.section("ParcelParser: replies that must be rejected (UNKNOWN)");
        T.eq("null", Aeb.UNKNOWN, ParcelParser.parseRead(null));
        T.eq("empty", Aeb.UNKNOWN, ParcelParser.parseRead(""));
        T.eq("nonzero status (exception code)", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel(ffffffff 00000001   '........')"));
        T.eq("value 2", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel(00000000 00000002   '........')"));
        T.eq("value ffffffff", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel(00000000 ffffffff   '........')"));
        T.eq("one word only", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel(00000000   '....')"));
        T.eq("three words (old parser accepted this)", Aeb.UNKNOWN,
                ParcelParser.parseRead("Result: Parcel(00000000 00000001 00000002   '............')"));
        T.eq("junk after the words (old parser accepted this)", Aeb.UNKNOWN,
                ParcelParser.parseRead("Result: Parcel(00000000 00000001 garbage)"));
        T.eq("two result lines, same value", Aeb.UNKNOWN,
                ParcelParser.parseRead("Result: Parcel(00000000 00000001 '........')\nResult: Parcel(00000000 00000001 '........')"));
        T.eq("two result lines, contradictory (old parser took the first)", Aeb.UNKNOWN,
                ParcelParser.parseRead("Result: Parcel(00000000 00000001 '........')\nResult: Parcel(00000000 00000000 '........')"));
        T.eq("bare 00000000 is not a reply (old queryAebStatus accepted it)", Aeb.UNKNOWN, ParcelParser.parseRead("00000000"));
        T.eq("bare 00000001", Aeb.UNKNOWN, ParcelParser.parseRead("00000001"));
        T.eq("multi-line parcel is not supported (same as before)", Aeb.UNKNOWN,
                ParcelParser.parseRead("Result: Parcel(\n  0x00000000: 00000000 00000001 '........')"));
        T.eq("error text", Aeb.UNKNOWN, ParcelParser.parseRead("Error: service 'autoservice' not found"));
        T.eq("short word", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel(0000000 00000001 '.......')"));
        T.eq("non hex word", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel(0000000g 00000001 '.......')"));
        T.eq("empty parcel", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel()"));
        T.eq("NULL parcel", Aeb.UNKNOWN, ParcelParser.parseRead("Result: Parcel(NULL)"));

        T.section("ParcelParser: isStatusOk (first word is the exception code)");
        T.isTrue("status only", ParcelParser.isStatusOk("Result: Parcel(00000000    '....')"));
        T.isTrue("status plus value", ParcelParser.isStatusOk("Result: Parcel(00000000 00000000   '........')"));
        T.isTrue("nonzero is not ok", !ParcelParser.isStatusOk("Result: Parcel(00000001    '....')"));
        T.isTrue("garbage is not ok", !ParcelParser.isStatusOk("nope"));
        T.isTrue("null is not ok", !ParcelParser.isStatusOk(null));
    }
}
