public class CFTest {
    public static void main(String[] args) {
        CFTest t = new CFTest();
        t.foo();
        System.out.println("Done");
    }

    public void foo() {
        int x = 0;
        for (int i = 0; i < 5; i++) {
            x += i;
        }
        if (x > 10) {
            System.out.println("Big: " + x);
        } else {
            System.out.println("Small: " + x);
        }
    }
}