import java.io.Serializable;
import java.util.List;

public class TestGen<T extends Serializable> {
    public List<T> items;

    public <U extends CharSequence> U echo(U input) {
        return input;
    }

    public static void main(String[] args) {
        System.out.println("TestGen OK");
    }
}
