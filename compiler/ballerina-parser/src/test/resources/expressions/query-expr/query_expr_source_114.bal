function testGroupBy() {
    var res = from var {price1, price2, name} in orders
        group by boolean _ = true, int _ = 3
        select name;
}
