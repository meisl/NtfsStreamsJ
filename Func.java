import java.util.regex.*;

public abstract class Func {

    public static final <S, T, TResult> Func1<S, TResult> compose(
        final Func1<? super T, ? extends TResult> outer,
        final Func1<? super S, ? extends T> inner
    ) {
        return new Func1<S, TResult>() {
            public TResult apply(S value) {
                return outer.apply(inner.apply(value));
            }
            public String toString() {
                return "\\v.( (" + outer + ") ((" + inner +  ") v) )";
            }
        };
    }

    public static final Func1<String, MatchResult> regexMatch(final Pattern p) {
        final Matcher m = p.matcher("");
        return new Func1<String, MatchResult>() {
            public MatchResult apply(String s) {
                return m.reset(s).matches() ? m.toMatchResult() : null;
            }
            public String toString() {
                return "/" + p + "/.match";
            }
        };
    }
    
    public static final Func1<MatchResult, Tuple3<String, String, String>> toTuple3() {
        return new Func1<MatchResult, Tuple3<String, String, String>>() {
            public Tuple3<String, String, String> apply(MatchResult m) {
                return Tuple.create(m.group(1), m.group(2), m.group(3));
            }
            public String toString() {
                return "\\m.(<m.group(1), m.group(2), m.group(3)>)";
            }
        };
    }
    
    public static final <T> Func3<Boolean, T, T, T> ite() {
        return new Func3<Boolean, T, T, T>() {
            public T apply(Boolean condition, T valThen, T valElse) {
                return condition ? valThen : valElse;
            }
            public String toString() {
                return "\\c t e.(c ? t : e)";
            }
        };
    }
    
    public static final <T> Func2<T, T, T> elvis() {
        return new Func2<T, T, T>() {
            public T apply(T value, T alternative) {
                return (value != null) ? value : alternative;
            }
        };
    }

}
