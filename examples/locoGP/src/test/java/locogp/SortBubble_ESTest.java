/*
 * This file was automatically generated by EvoSuite
 * Sat Jun 06 18:29:12 GMT 2020
 */

package locogp;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.evosuite.runtime.EvoAssertions.*;
import locogp.SortBubble;
import org.evosuite.runtime.EvoRunner;
import org.evosuite.runtime.EvoRunnerParameters;
import org.junit.runner.RunWith;

@RunWith(EvoRunner.class) @EvoRunnerParameters(mockJVMNonDeterminism = true, useVFS = true, useVNET = true, resetStaticState = true, useJEE = true) 
public class SortBubble_ESTest extends SortBubble_ESTest_scaffolding {

  @Test(timeout = 4000)
  public void test0()  throws Throwable  {
      SortBubble sortBubble0 = new SortBubble();
      assertNotNull(sortBubble0);
  }

  @Test(timeout = 4000)
  public void test1()  throws Throwable  {
      Integer[] integerArray0 = new Integer[4];
      Integer integer0 = new Integer(1130);
      assertEquals(1130, (int)integer0);
      assertNotNull(integer0);
      
      integerArray0[0] = integer0;
      integerArray0[1] = integer0;
      Integer integer1 = new Integer((-50));
      assertEquals((-50), (int)integer1);
      assertFalse(integer1.equals((Object)integer0));
      assertNotNull(integer1);
      
      integerArray0[2] = integer1;
      // Undeclared exception!
      try { 
        SortBubble.sort(integerArray0, integer0);
        fail("Expecting exception: NullPointerException");
      
      } catch(NullPointerException e) {
         //
         // no message in exception (getMessage() returned null)
         //
         verifyException("locogp.SortBubble", e);
      }
  }

  @Test(timeout = 4000)
  public void test2()  throws Throwable  {
      Integer[] integerArray0 = new Integer[1];
      Integer integer0 = new Integer((-956));
      assertEquals((-956), (int)integer0);
      assertNotNull(integer0);
      
      integerArray0[0] = integer0;
      Integer[] integerArray1 = SortBubble.sort(integerArray0, integerArray0[0]);
      assertEquals(1, integerArray0.length);
      assertEquals(1, integerArray1.length);
      assertSame(integerArray0, integerArray1);
      assertSame(integerArray1, integerArray0);
      assertNotNull(integerArray1);
  }

  @Test(timeout = 4000)
  public void test3()  throws Throwable  {
      Integer[] integerArray0 = new Integer[3];
      Integer integer0 = new Integer(0);
      assertEquals(0, (int)integer0);
      assertNotNull(integer0);
      
      integerArray0[0] = integer0;
      integerArray0[1] = integerArray0[0];
      Integer integer1 = new Integer(2782);
      assertEquals(2782, (int)integer1);
      assertFalse(integer1.equals((Object)integer0));
      assertNotNull(integer1);
      
      integerArray0[2] = integer1;
      // Undeclared exception!
      try { 
        SortBubble.sort(integerArray0, integerArray0[2]);
        fail("Expecting exception: ArrayIndexOutOfBoundsException");
      
      } catch(ArrayIndexOutOfBoundsException e) {
         //
         // 3
         //
         verifyException("locogp.SortBubble", e);
      }
  }

  @Test(timeout = 4000)
  public void test4()  throws Throwable  {
      Integer[] integerArray0 = new Integer[4];
      Integer integer0 = new Integer(1);
      assertEquals(1, (int)integer0);
      assertNotNull(integer0);
      
      Integer[] integerArray1 = SortBubble.sort(integerArray0, integer0);
      assertEquals(4, integerArray0.length);
      assertEquals(4, integerArray1.length);
      assertSame(integerArray0, integerArray1);
      assertSame(integerArray1, integerArray0);
      assertNotNull(integerArray1);
  }
}