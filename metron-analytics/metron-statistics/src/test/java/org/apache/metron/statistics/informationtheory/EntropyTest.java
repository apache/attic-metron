package org.apache.metron.statistics.informationtheory;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

import static org.apache.metron.stellar.common.utils.StellarProcessorUtils.run;

public class EntropyTest {
  @Test
  public void entropyTest() throws Exception {
    //test empty collection
    Assert.assertEquals(0.0, (Double) run("IT_ENTROPY({})", new HashMap<>()), 0.0);

    /*
    Now consider the string aaaaaaaaaabbbbbccccc or 10 a's followed by 5 b's and 5 c's.
    The probabilities of each character is as follows:
    p(a) = 1/2
    p(b) = 1/4
    p(c) = 1/4
    so the shannon entropy should be
      -p(a)*log_2(p(a)) - p(b)*log_2(p(b)) - p(c)*log_2(p(c)) =
      -0.5*-1 - 0.25*-2 - 0.25*-2 = 1.5
     */
    Assert.assertEquals(1.5, (Double) run("IT_ENTROPY({ 'a' : 10, 'b' : 5, 'c' : 5} )", new HashMap<>()), 0.0);
  }
}
