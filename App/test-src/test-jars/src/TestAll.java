package demo;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

// Аннотация (пункт 2.4)
@interface CustomAnno {
    String value();
}

@CustomAnno("class")
public class TestAll<T extends Serializable> {      // дженерик на уровне класса (2.1)

    @CustomAnno("field")
    public List<T> list;                             // дженерик-поле (2.1)

    public TestAll(List<T> list) {                   // конструктор с локальной переменной (2.2)
        this.list = list;
    }

    public <U extends CharSequence> U echo(U input) { // дженерик-метод (2.1)
        String local = input.toString();               // локальная переменная (2.2)
        return input;
    }

    public void lambdaDemo() {                        // лямбда → invokedynamic (2.3)
        Function<String,Integer> f = s -> s.length();
        System.out.println(f.apply("lambda"));
    }

    public void localLoopDemo() {                     // ещё лок.переменные в цикле
        int n = 3;
        for (int i = 0; i < n; i++) {
            String msg = "i=" + i;
            System.out.println(msg);
        }
    }

    @CustomAnno("inner")
    public class Inner {                              // inner class (2.4)
        public void hi() {
            System.out.println("Hi from Inner");
        }
    }

    public static void main(String[] args) {
        System.out.println("TestAll OK");
        TestAll<String> t = new TestAll<>(List.of("x"));
        TestAll<String>.Inner i = t.new Inner();
        i.hi();
    }
}
