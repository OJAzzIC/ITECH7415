package vocab;

import java.util.HashSet;
import java.util.Objects;

public class SES {
    private static final SES SES_PROFESSIONAL = SES.create("Professional", 30142);
    private static final SES SES_WELFARE = SES.create("Welfare", 8767);
    private static final SES SES_WORKING = SES.create("Working", 17514);

    private String name;
    private int qty;

    private SES() {
    }

    public static SES create(String sesName, int sesQty) {
        if (sesName == null || sesName.isBlank())
            throw new IllegalArgumentException("'name' must contain a value.");
        if (sesQty < 0)
            throw new IllegalArgumentException("'qty' must be greater than 0");
        SES output = new SES();
        output.name = sesName;
        output.qty = sesQty;
        return output;
    }

    public static HashSet<SES> defaults() {
        HashSet<SES> values = new HashSet<>();
        values.add(SES_WELFARE);
        values.add(SES_WORKING);
        values.add(SES_PROFESSIONAL);
        return values;
    }

    public String getName() {
        return name;
    }

    public int getQty() {
        return qty;
    }

    @Override
    public boolean equals(Object other) {
        // Null objects will never be the same as this
        if (other == null)
            return false;
        // 'other' references the same object as 'this'
        if (this == other)
            return true;
        // 'other' doesn't reference an SES object
        if (!(other instanceof SES))
            return false;
        // 'this' and 'other' are equal if the names match
        return name.equals(((SES) other).name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return String.format("%s (%,d)", name, qty);
    }
}