/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.test;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.schema.impl.ViewTable;
import org.apache.calcite.util.Smalls;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests for user-defined functions (including user-defined table functions
 * and user-defined aggregate functions).
 *
 * @see Smalls
 */
public class UdfTest {
  private CalciteAssert.AssertThat withUdf() {
    final String model = "{\n"
        + "  version: '1.0',\n"
        + "   schemas: [\n"
        + "     {\n"
        + "       name: 'adhoc',\n"
        + "       tables: [\n"
        + "         {\n"
        + "           name: 'EMPLOYEES',\n"
        + "           type: 'custom',\n"
        + "           factory: '"
        + JdbcTest.EmpDeptTableFactory.class.getName()
        + "',\n"
        + "           operand: {'foo': true, 'bar': 345}\n"
        + "         }\n"
        + "       ],\n"
        + "       functions: [\n"
        + "         {\n"
        + "           name: 'MY_PLUS',\n"
        + "           className: '"
        + Smalls.MyPlusFunction.class.getName()
        + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'MY_LEFT',\n"
        + "           className: '"
        + Smalls.MyLeftFunction.class.getName()
        + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'ABCDE',\n"
        + "           className: '"
        + Smalls.MyAbcdeFunction.class.getName()
        + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'MY_STR',\n"
        + "           className: '"
        + Smalls.MyToStringFunction.class.getName()
        + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'MY_DOUBLE',\n"
        + "           className: '"
        + Smalls.MyDoubleFunction.class.getName()
        + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'COUNT_ARGS',\n"
        + "           className: '"
        + Smalls.CountArgs0Function.class.getName()
        + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'COUNT_ARGS',\n"
        + "           className: '"
        + Smalls.CountArgs1Function.class.getName()
        + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'COUNT_ARGS',\n"
        + "           className: '"
        + Smalls.CountArgs1NullableFunction.class.getName()
        + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'COUNT_ARGS',\n"
        + "           className: '"
        + Smalls.CountArgs2Function.class.getName()
        + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'MY_ABS',\n"
        + "           className: '"
        + java.lang.Math.class.getName()
        + "',\n"
        + "           methodName: 'abs'\n"
        + "         },\n"
        + "         {\n"
        + "           className: '"
        + Smalls.MultipleFunction.class.getName()
        + "',\n"
        + "           methodName: '*'\n"
        + "         },\n"
        + "         {\n"
        + "           className: '"
        + Smalls.AllTypesFunction.class.getName()
        + "',\n"
        + "           methodName: '*'\n"
        + "         }\n"
        + "       ]\n"
        + "     }\n"
        + "   ]\n"
        + "}";
    return CalciteAssert.model(model);
  }

  /** Tests user-defined function. */
  @Test public void testUserDefinedFunction() throws Exception {
    final String sql = "select \"adhoc\".my_plus(\"deptno\", 100) as p\n"
        + "from \"adhoc\".EMPLOYEES";
    final String expected = "P=110\n"
            + "P=120\n"
            + "P=110\n"
            + "P=110\n";
    withUdf().query(sql).returns(expected);
  }

  @Test public void testUserDefinedFunctionB() throws Exception {
    final String sql = "select \"adhoc\".my_double(\"deptno\") as p\n"
        + "from \"adhoc\".EMPLOYEES";
    final String expected = "P=20\n"
        + "P=40\n"
        + "P=20\n"
        + "P=20\n";
    withUdf().query(sql).returns(expected);
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-937">[CALCITE-937]
   * User-defined function within view</a>. */
  @Test public void testUserDefinedFunctionInView() throws Exception {
    Class.forName("org.apache.calcite.jdbc.Driver");
    Connection connection = DriverManager.getConnection("jdbc:calcite:");
    CalciteConnection calciteConnection =
        connection.unwrap(CalciteConnection.class);
    SchemaPlus rootSchema = calciteConnection.getRootSchema();
    rootSchema.add("hr", new ReflectiveSchema(new JdbcTest.HrSchema()));

    SchemaPlus post = rootSchema.add("POST", new AbstractSchema());
    post.add("MY_INCREMENT",
        ScalarFunctionImpl.create(Smalls.MyIncrement.class, "eval"));

    final String viewSql = "select \"empid\" as EMPLOYEE_ID,\n"
        + "  \"name\" || ' ' || \"name\" as EMPLOYEE_NAME,\n"
        + "  \"salary\" as EMPLOYEE_SALARY,\n"
        + "  POST.MY_INCREMENT(\"empid\", 10) as INCREMENTED_SALARY\n"
        + "from \"hr\".\"emps\"";
    post.add("V_EMP",
        ViewTable.viewMacro(post, viewSql, ImmutableList.<String>of(), null));

    final String result = ""
        + "EMPLOYEE_ID=100; EMPLOYEE_NAME=Bill Bill; EMPLOYEE_SALARY=10000.0; INCREMENTED_SALARY=110.0\n"
        + "EMPLOYEE_ID=200; EMPLOYEE_NAME=Eric Eric; EMPLOYEE_SALARY=8000.0; INCREMENTED_SALARY=220.0\n"
        + "EMPLOYEE_ID=150; EMPLOYEE_NAME=Sebastian Sebastian; EMPLOYEE_SALARY=7000.0; INCREMENTED_SALARY=165.0\n"
        + "EMPLOYEE_ID=110; EMPLOYEE_NAME=Theodore Theodore; EMPLOYEE_SALARY=11500.0; INCREMENTED_SALARY=121.0\n";

    Statement statement = connection.createStatement();
    ResultSet resultSet = statement.executeQuery(viewSql);
    assertThat(CalciteAssert.toString(resultSet), is(result));
    resultSet.close();

    ResultSet viewResultSet =
        statement.executeQuery("select * from \"POST\".\"V_EMP\"");
    assertThat(CalciteAssert.toString(viewResultSet), is(result));
    statement.close();
    connection.close();
  }

  /**
   * Tests that IS NULL/IS NOT NULL is properly implemented for non-strict
   * functions.
   */
  @Test public void testNotNullImplementor() {
    final CalciteAssert.AssertThat with = withUdf();
    with.query(
        "select upper(\"adhoc\".my_str(\"name\")) as p from \"adhoc\".EMPLOYEES")
        .returns("P=<BILL>\n"
            + "P=<ERIC>\n"
            + "P=<SEBASTIAN>\n"
            + "P=<THEODORE>\n");
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n"
        + "where \"adhoc\".my_str(\"name\") is not null")
        .returns("P=Bill\n"
            + "P=Eric\n"
            + "P=Sebastian\n"
            + "P=Theodore\n");
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n"
        + "where \"adhoc\".my_str(upper(\"name\")) is not null")
        .returns("P=Bill\n"
            + "P=Eric\n"
            + "P=Sebastian\n"
            + "P=Theodore\n");
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n"
        + "where upper(\"adhoc\".my_str(\"name\")) is not null")
        .returns("P=Bill\n"
            + "P=Eric\n"
            + "P=Sebastian\n"
            + "P=Theodore\n");
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n"
        + "where \"adhoc\".my_str(\"name\") is null")
        .returns("");
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n"
        + "where \"adhoc\".my_str(upper(\"adhoc\".my_str(\"name\")"
        + ")) ='8'")
        .returns("");
  }

  /** Tests derived return type of user-defined function. */
  @Test public void testUdfDerivedReturnType() {
    final CalciteAssert.AssertThat with = withUdf();
    with.query(
        "select max(\"adhoc\".my_double(\"deptno\")) as p from \"adhoc\".EMPLOYEES")
        .returns("P=40\n");
    with.query("select max(\"adhoc\".my_str(\"name\")) as p\n"
        + "from \"adhoc\".EMPLOYEES\n"
        + "where \"adhoc\".my_str(\"name\") is null")
        .returns("P=null\n");
  }

  /** Tests a user-defined function that has multiple overloads. */
  @Test public void testUdfOverloaded() {
    final CalciteAssert.AssertThat with = withUdf();
    with.query("values (\"adhoc\".count_args(),\n"
        + " \"adhoc\".count_args(0),\n"
        + " \"adhoc\".count_args(0, 0))")
        .returns("EXPR$0=0; EXPR$1=1; EXPR$2=2\n");
    with.query("select max(\"adhoc\".count_args()) as p0,\n"
        + " min(\"adhoc\".count_args(0)) as p1,\n"
        + " max(\"adhoc\".count_args(0, 0)) as p2\n"
        + "from \"adhoc\".EMPLOYEES limit 1")
        .returns("P0=0; P1=1; P2=2\n");
  }

  @Test public void testUdfOverloadedNullable() {
    final CalciteAssert.AssertThat with = withUdf();
    with.query("values (\"adhoc\".count_args(),\n"
        + " \"adhoc\".count_args(cast(null as smallint)),\n"
        + " \"adhoc\".count_args(0, 0))")
        .returns("EXPR$0=0; EXPR$1=-1; EXPR$2=2\n");
  }

  /** Tests passing parameters to user-defined function by name. */
  @Test public void testUdfArgumentName() {
    final CalciteAssert.AssertThat with = withUdf();
    // arguments in physical order
    with.query("values (\"adhoc\".my_left(\"s\" => 'hello', \"n\" => 3))")
        .returns("EXPR$0=hel\n");
    // arguments in reverse order
    with.query("values (\"adhoc\".my_left(\"n\" => 3, \"s\" => 'hello'))")
        .returns("EXPR$0=hel\n");
    with.query("values (\"adhoc\".my_left(\"n\" => 1 + 2, \"s\" => 'hello'))")
        .returns("EXPR$0=hel\n");
    // duplicate argument names
    with.query("values (\"adhoc\".my_left(\"n\" => 3, \"n\" => 2, \"s\" => 'hello'))")
        .throws_("Duplicate argument name 'n'");
    // invalid argument names
    with.query("values (\"adhoc\".my_left(\"n\" => 3, \"m\" => 2, \"s\" => 'h'))")
        .throws_("No match found for function signature "
            + "MY_LEFT(n => <NUMERIC>, m => <NUMERIC>, s => <CHARACTER>)");
    // missing arguments
    with.query("values (\"adhoc\".my_left(\"n\" => 3))")
        .throws_("No match found for function signature MY_LEFT(n => <NUMERIC>)");
    with.query("values (\"adhoc\".my_left(\"s\" => 'hello'))")
        .throws_("No match found for function signature MY_LEFT(s => <CHARACTER>)");
    // arguments of wrong type
    with.query("values (\"adhoc\".my_left(\"n\" => 'hello', \"s\" => 'x'))")
        .throws_("No match found for function signature "
            + "MY_LEFT(n => <CHARACTER>, s => <CHARACTER>)");
    with.query("values (\"adhoc\".my_left(\"n\" => 1, \"s\" => 0))")
        .throws_("No match found for function signature "
            + "MY_LEFT(n => <NUMERIC>, s => <NUMERIC>)");
  }

  /** Tests calling a user-defined function some of whose parameters are
   * optional. */
  @Test public void testUdfArgumentOptional() {
    final CalciteAssert.AssertThat with = withUdf();
    with.query("values (\"adhoc\".abcde(a=>1,b=>2,c=>3,d=>4,e=>5))")
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: 5}\n");
    with.query("values (\"adhoc\".abcde(1,2,3,4,CAST(NULL AS INTEGER)))")
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: null}\n");
    with.query("values (\"adhoc\".abcde(a=>1,b=>2,c=>3,d=>4))")
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: null}\n");
    with.query("values (\"adhoc\".abcde(a=>1,b=>2,c=>3))")
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: null, e: null}\n");
    with.query("values (\"adhoc\".abcde(a=>1,e=>5,c=>3))")
        .returns("EXPR$0={a: 1, b: null, c: 3, d: null, e: 5}\n");
    with.query("values (\"adhoc\".abcde(1,2,3))")
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: null, e: null}\n");
    with.query("values (\"adhoc\".abcde(1,2,3,4))")
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: null}\n");
    with.query("values (\"adhoc\".abcde(1,2,3,4,5))")
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: 5}\n");
    with.query("values (\"adhoc\".abcde(1,2))")
        .throws_("No match found for function signature ABCDE(<NUMERIC>, <NUMERIC>)");
    with.query("values (\"adhoc\".abcde(1,DEFAULT,3))")
        .returns("EXPR$0={a: 1, b: null, c: 3, d: null, e: null}\n");
    with.query("values (\"adhoc\".abcde(1,DEFAULT,'abcde'))")
        .throws_("No match found for function signature ABCDE(<NUMERIC>, <ANY>, <CHARACTER>)");
    with.query("values (\"adhoc\".abcde(true))")
        .throws_("No match found for function signature ABCDE(<BOOLEAN>)");
    with.query("values (\"adhoc\".abcde(true,DEFAULT))")
        .throws_("No match found for function signature ABCDE(<BOOLEAN>, <ANY>)");
    with.query("values (\"adhoc\".abcde(1,DEFAULT,3,DEFAULT))")
        .returns("EXPR$0={a: 1, b: null, c: 3, d: null, e: null}\n");
    with.query("values (\"adhoc\".abcde(1,2,DEFAULT))")
        .throws_("DEFAULT is only allowed for optional parameters");
    with.query("values (\"adhoc\".abcde(a=>1,b=>2,c=>DEFAULT))")
        .throws_("DEFAULT is only allowed for optional parameters");
    with.query("values (\"adhoc\".abcde(a=>1,b=>DEFAULT,c=>3))")
        .returns("EXPR$0={a: 1, b: null, c: 3, d: null, e: null}\n");
  }

  /** Test for
   * {@link org.apache.calcite.runtime.CalciteResource#requireDefaultConstructor(String)}. */
  @Test public void testUserDefinedFunction2() throws Exception {
    withBadUdf(Smalls.AwkwardFunction.class)
        .connectThrows(
            "Declaring class 'org.apache.calcite.util.Smalls$AwkwardFunction' of non-static user-defined function must have a public constructor with zero parameters");
  }

  /** Tests user-defined function, with multiple methods per class. */
  @Test public void testUserDefinedFunctionWithMethodName() throws Exception {
    // java.lang.Math has abs(int) and abs(double).
    final CalciteAssert.AssertThat with = withUdf();
    with.query("values abs(-4)").returnsValue("4");
    with.query("values abs(-4.5)").returnsValue("4.5");

    // 3 overloads of "fun1", another method "fun2", but method "nonStatic"
    // cannot be used as a function
    with.query("values \"adhoc\".\"fun1\"(2)").returnsValue("4");
    with.query("values \"adhoc\".\"fun1\"(2, 3)").returnsValue("5");
    with.query("values \"adhoc\".\"fun1\"('Foo Bar')").returnsValue("foo bar");
    with.query("values \"adhoc\".\"fun2\"(10)").returnsValue("30");
    with.query("values \"adhoc\".\"nonStatic\"(2)")
        .throws_("No match found for function signature nonStatic(<NUMERIC>)");
  }

  /** Tests user-defined aggregate function. */
  @Test public void testUserDefinedAggregateFunction() throws Exception {
    final String empDept = JdbcTest.EmpDeptTableFactory.class.getName();
    final String sum = Smalls.MyStaticSumFunction.class.getName();
    final String sum2 = Smalls.MySumFunction.class.getName();
    final CalciteAssert.AssertThat with = CalciteAssert.model("{\n"
        + "  version: '1.0',\n"
        + "   schemas: [\n"
        + "     {\n"
        + "       name: 'adhoc',\n"
        + "       tables: [\n"
        + "         {\n"
        + "           name: 'EMPLOYEES',\n"
        + "           type: 'custom',\n"
        + "           factory: '" + empDept + "',\n"
        + "           operand: {'foo': true, 'bar': 345}\n"
        + "         }\n"
        + "       ],\n"
        + "       functions: [\n"
        + "         {\n"
        + "           name: 'MY_SUM',\n"
        + "           className: '" + sum + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'MY_SUM2',\n"
        + "           className: '" + sum2 + "'\n"
        + "         }\n"
        + "       ]\n"
        + "     }\n"
        + "   ]\n"
        + "}")
        .withDefaultSchema("adhoc");
    with.withDefaultSchema(null)
        .query(
            "select \"adhoc\".my_sum(\"deptno\") as p from \"adhoc\".EMPLOYEES\n")
        .returns("P=50\n");
    with.query("select my_sum(\"empid\"), \"deptno\" as p from EMPLOYEES\n")
        .throws_(
            "Expression 'deptno' is not being grouped");
    with.query("select my_sum(\"deptno\") as p from EMPLOYEES\n")
        .returns("P=50\n");
    with.query("select my_sum(\"name\") as p from EMPLOYEES\n")
        .throws_(
            "Cannot apply 'MY_SUM' to arguments of type 'MY_SUM(<JAVATYPE(CLASS JAVA.LANG.STRING)>)'. Supported form(s): 'MY_SUM(<NUMERIC>)");
    with.query("select my_sum(\"deptno\", 1) as p from EMPLOYEES\n")
        .throws_(
            "No match found for function signature MY_SUM(<NUMERIC>, <NUMERIC>)");
    with.query("select my_sum() as p from EMPLOYEES\n")
        .throws_(
            "No match found for function signature MY_SUM()");
    with.query("select \"deptno\", my_sum(\"deptno\") as p from EMPLOYEES\n"
        + "group by \"deptno\"")
        .returnsUnordered(
            "deptno=20; P=20",
            "deptno=10; P=30");
    with.query("select \"deptno\", my_sum2(\"deptno\") as p from EMPLOYEES\n"
        + "group by \"deptno\"")
        .returnsUnordered("deptno=20; P=20", "deptno=10; P=30");
  }

  /** Test for
   * {@link org.apache.calcite.runtime.CalciteResource#firstParameterOfAdd(String)}. */
  @Test public void testUserDefinedAggregateFunction3() throws Exception {
    withBadUdf(Smalls.SumFunctionBadIAdd.class).connectThrows(
        "Caused by: java.lang.RuntimeException: In user-defined aggregate class 'org.apache.calcite.util.Smalls$SumFunctionBadIAdd', first parameter to 'add' method must be the accumulator (the return type of the 'init' method)");
  }

  private static CalciteAssert.AssertThat withBadUdf(Class clazz) {
    final String empDept = JdbcTest.EmpDeptTableFactory.class.getName();
    final String className = clazz.getName();
    return CalciteAssert.model("{\n"
        + "  version: '1.0',\n"
        + "   schemas: [\n"
        + "     {\n"
        + "       name: 'adhoc',\n"
        + "       tables: [\n"
        + "         {\n"
        + "           name: 'EMPLOYEES',\n"
        + "           type: 'custom',\n"
        + "           factory: '" + empDept + "',\n"
        + "           operand: {'foo': true, 'bar': 345}\n"
        + "         }\n"
        + "       ],\n"
        + "       functions: [\n"
        + "         {\n"
        + "           name: 'AWKWARD',\n"
        + "           className: '" + className + "'\n"
        + "         }\n"
        + "       ]\n"
        + "     }\n"
        + "   ]\n"
        + "}")
        .withDefaultSchema("adhoc");
  }

  /** Tests user-defined aggregate function with FILTER.
   *
   * <p>Also tests that we do not try to push ADAF to JDBC source. */
  @Test public void testUserDefinedAggregateFunctionWithFilter() throws Exception {
    final String sum = Smalls.MyStaticSumFunction.class.getName();
    final String sum2 = Smalls.MySumFunction.class.getName();
    final CalciteAssert.AssertThat with = CalciteAssert.model("{\n"
        + "  version: '1.0',\n"
        + "   schemas: [\n"
        + JdbcTest.SCOTT_SCHEMA
        + ",\n"
        + "     {\n"
        + "       name: 'adhoc',\n"
        + "       functions: [\n"
        + "         {\n"
        + "           name: 'MY_SUM',\n"
        + "           className: '" + sum + "'\n"
        + "         },\n"
        + "         {\n"
        + "           name: 'MY_SUM2',\n"
        + "           className: '" + sum2 + "'\n"
        + "         }\n"
        + "       ]\n"
        + "     }\n"
        + "   ]\n"
        + "}")
        .withDefaultSchema("adhoc");
    with.query("select deptno, \"adhoc\".my_sum(deptno) as p\n"
        + "from scott.emp\n"
        + "group by deptno\n")
        .returns(
            "DEPTNO=20; P=100\n"
                + "DEPTNO=10; P=30\n"
                + "DEPTNO=30; P=180\n");

    with.query("select deptno,\n"
        + "  \"adhoc\".my_sum(deptno) filter (where job = 'CLERK') as c,\n"
        + "  \"adhoc\".my_sum(deptno) filter (where job = 'XXX') as x\n"
        + "from scott.emp\n"
        + "group by deptno\n")
        .returns(
            "DEPTNO=20; C=40; X=0\n"
                + "DEPTNO=10; C=10; X=0\n"
                + "DEPTNO=30; C=30; X=0\n");
  }

  /** Tests resolution of functions using schema paths. */
  @Test public void testPath() throws Exception {
    final String name = Smalls.MyPlusFunction.class.getName();
    final CalciteAssert.AssertThat with = CalciteAssert.model("{\n"
        + "  version: '1.0',\n"
        + "   schemas: [\n"
        + "     {\n"
        + "       name: 'adhoc',\n"
        + "       functions: [\n"
        + "         {\n"
        + "           name: 'MY_PLUS',\n"
        + "           className: '" + name + "'\n"
        + "         }\n"
        + "       ]\n"
        + "     },\n"
        + "     {\n"
        + "       name: 'adhoc2',\n"
        + "       functions: [\n"
        + "         {\n"
        + "           name: 'MY_PLUS2',\n"
        + "           className: '" + name + "'\n"
        + "         }\n"
        + "       ]\n"
        + "     },\n"
        + "     {\n"
        + "       name: 'adhoc3',\n"
        + "       path: ['adhoc2','adhoc3'],\n"
        + "       functions: [\n"
        + "         {\n"
        + "           name: 'MY_PLUS3',\n"
        + "           className: '" + name + "'\n"
        + "         }\n"
        + "       ]\n"
        + "     }\n"
        + "   ]\n"
        + "}");

    final String err = "No match found for function signature";
    final String res = "EXPR$0=2\n";

    // adhoc can see own function MY_PLUS but not adhoc2.MY_PLUS2 unless
    // qualified
    final CalciteAssert.AssertThat adhoc = with.withDefaultSchema("adhoc");
    adhoc.query("values MY_PLUS(1, 1)").returns(res);
    adhoc.query("values MY_PLUS2(1, 1)").throws_(err);
    adhoc.query("values \"adhoc2\".MY_PLUS(1, 1)").throws_(err);
    adhoc.query("values \"adhoc2\".MY_PLUS2(1, 1)").returns(res);

    // adhoc2 can see own function MY_PLUS2 but not adhoc2.MY_PLUS unless
    // qualified
    final CalciteAssert.AssertThat adhoc2 = with.withDefaultSchema("adhoc2");
    adhoc2.query("values MY_PLUS2(1, 1)").returns(res);
    adhoc2.query("values MY_PLUS(1, 1)").throws_(err);
    adhoc2.query("values \"adhoc\".MY_PLUS(1, 1)").returns(res);

    // adhoc3 can see own adhoc2.MY_PLUS2 because in path, with or without
    // qualification, but can only see adhoc.MY_PLUS with qualification
    final CalciteAssert.AssertThat adhoc3 = with.withDefaultSchema("adhoc3");
    adhoc3.query("values MY_PLUS2(1, 1)").returns(res);
    adhoc3.query("values MY_PLUS(1, 1)").throws_(err);
    adhoc3.query("values \"adhoc\".MY_PLUS(1, 1)").returns(res);
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-986">[CALCITE-986]
   * User-defined function with Date or Timestamp parameters</a>. */
  @Test public void testDate() {
    final CalciteAssert.AssertThat with = withUdf();
    with.query("values \"adhoc\".\"dateFun\"(DATE '1970-01-01')")
        .returnsValue("0");
    with.query("values \"adhoc\".\"dateFun\"(DATE '1970-01-02')")
        .returnsValue("86400000");
    with.query("values \"adhoc\".\"dateFun\"(cast(null as date))")
        .returnsValue("-1");
    with.query("values \"adhoc\".\"timeFun\"(TIME '00:00:00')")
        .returnsValue("0");
    with.query("values \"adhoc\".\"timeFun\"(TIME '00:01:30')")
        .returnsValue("90000");
    with.query("values \"adhoc\".\"timeFun\"(cast(null as time))")
        .returnsValue("-1");
    with.query("values \"adhoc\".\"timestampFun\"(TIMESTAMP '1970-01-01 00:00:00')")
        .returnsValue("0");
    with.query("values \"adhoc\".\"timestampFun\"(TIMESTAMP '1970-01-02 00:01:30')")
        .returnsValue("86490000");
    with.query("values \"adhoc\".\"timestampFun\"(cast(null as timestamp))")
        .returnsValue("-1");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1041">[CALCITE-1041]
   * User-defined function returns DATE or TIMESTAMP value</a>. */
  @Test public void testReturnDate() {
    final CalciteAssert.AssertThat with = withUdf();
    with.query("values \"adhoc\".\"toDateFun\"(0)")
        .returnsValue("1970-01-01");
    with.query("values \"adhoc\".\"toDateFun\"(1)")
        .returnsValue("1970-01-02");
    with.query("values \"adhoc\".\"toDateFun\"(cast(null as bigint))")
        .returnsValue(null);
    with.query("values \"adhoc\".\"toTimeFun\"(0)")
        .returnsValue("00:00:00");
    with.query("values \"adhoc\".\"toTimeFun\"(90000)")
        .returnsValue("00:01:30");
    with.query("values \"adhoc\".\"toTimeFun\"(cast(null as bigint))")
        .returnsValue(null);
    with.query("values \"adhoc\".\"toTimestampFun\"(0)")
        .returnsValue("1970-01-01 00:00:00");
    with.query("values \"adhoc\".\"toTimestampFun\"(86490000)")
        .returnsValue("1970-01-02 00:01:30");
    with.query("values \"adhoc\".\"toTimestampFun\"(cast(null as bigint))")
        .returnsValue(null);
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1041">[CALCITE-1041]
   * User-defined function returns DATE or TIMESTAMP value</a>. */
  @Test public void testReturnDate2() {
    final CalciteAssert.AssertThat with = withUdf();
    with.query("select * from (values 0) as t(c)\n"
        + "where \"adhoc\".\"toTimestampFun\"(c) in (\n"
        + "  cast('1970-01-01 00:00:00' as timestamp),\n"
        + "  cast('1997-02-01 00:00:00' as timestamp))")
        .returnsValue("0");
    with.query("select * from (values 0) as t(c)\n"
        + "where \"adhoc\".\"toTimestampFun\"(c) in (\n"
        + "  timestamp '1970-01-01 00:00:00',\n"
        + "  timestamp '1997-02-01 00:00:00')")
        .returnsValue("0");
    with.query("select * from (values 0) as t(c)\n"
        + "where \"adhoc\".\"toTimestampFun\"(c) in (\n"
        + "  '1970-01-01 00:00:00',\n"
        + "  '1997-02-01 00:00:00')")
        .returnsValue("0");
  }

}

// End UdfTest.java
