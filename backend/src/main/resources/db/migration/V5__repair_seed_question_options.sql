-- 修复旧版演示数据中被 MySQL 吞掉反斜杠的 Unicode 选项。
-- 同时精确匹配题号、题干及完整的损坏选项；正常数据和用户修改过的题目不覆盖。
-- 仅更新选项，保留更新时间、答案、成绩及考试记录。可安全重复执行。
-- 后续初始化数据直接使用 UTF-8 字符，不再在 SQL 字符串中写单反斜杠 Unicode 转义。

UPDATE questions
SET options = '["A. 选择", "B. 投影", "C. 连接", "D. 排序"]', updated_at = updated_at
WHERE id = 1
  AND CAST(title AS BINARY) = CAST('关系数据库中，下列哪项不是基本的关系操作？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u9009u62e9", "B. u6295u5f71", "C. u8fdeu63a5", "D. u6392u5e8f"]' AS BINARY);

UPDATE questions
SET options = '["A. DROP TABLE", "B. DELETE FROM", "C. TRUNCATE TABLE", "D. B和C均可"]', updated_at = updated_at
WHERE id = 2
  AND CAST(title AS BINARY) = CAST('SQL中，以下哪个语句用于删除表中的数据而保留表结构？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. DROP TABLE", "B. DELETE FROM", "C. TRUNCATE TABLE", "D. Bu548cCu5747u53ef"]' AS BINARY);

UPDATE questions
SET options = '["A. 原子性保证事务内操作要么全部成功，要么全部失败", "B. 一致性是指数据库在任何时刻都处于一致状态", "C. 隔离性要求事务之间完全隔离，不能并发", "D. 持久性仅保证已提交事务在内存中的持久"]', updated_at = updated_at
WHERE id = 3
  AND CAST(title AS BINARY) = CAST('下列关于事务ACID特性的描述，哪项是正确的？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u539fu5b50u6027u4fddu8bc1u4e8bu52a1u5185u64cdu4f5cu8981u4e48u5168u90e8u6210u529fuff0cu8981u4e48u5168u90e8u5931u8d25", "B. u4e00u81f4u6027u662fu6307u6570u636eu5e93u5728u4efbu4f55u65f6u523bu90fdu5904u4e8eu4e00u81f4u72b6u6001", "C. u9694u79bbu6027u8981u6c42u4e8bu52a1u4e4bu95f4u5b8cu5168u9694u79bbuff0cu4e0du80fdu5e76u53d1", "D. u6301u4e45u6027u4ec5u4fddu8bc1u5df2u63d0u4ea4u4e8bu52a1u5728u5185u5b58u4e2du7684u6301u4e45"]' AS BINARY);

UPDATE questions
SET options = '["A. 每个属性都是原子的", "B. 消除部分函数依赖", "C. 消除传递函数依赖", "D. 消除多值依赖"]', updated_at = updated_at
WHERE id = 4
  AND CAST(title AS BINARY) = CAST('在数据库设计中，第三范式(3NF)要求：' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u6bcfu4e2au5c5eu6027u90fdu662fu539fu5b50u7684", "B. u6d88u9664u90e8u5206u51fdu6570u4f9du8d56", "C. u6d88u9664u4f20u9012u51fdu6570u4f9du8d56", "D. u6d88u9664u591au503cu4f9du8d56"]' AS BINARY);

UPDATE questions
SET options = '["A. 在高选择性字段（如用户ID）上的等值查询", "B. 在低选择性字段（如性别字段只有男/女）上的查询", "C. 范围查询（BETWEEN, >, <）", "D. 联合索引的最左前缀查询"]', updated_at = updated_at
WHERE id = 6
  AND CAST(title AS BINARY) = CAST('索引能够提高查询性能，以下场景中，索引效果最差的是？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u5728u9ad8u9009u62e9u6027u5b57u6bb5uff08u5982u7528u6237IDuff09u4e0au7684u7b49u503cu67e5u8be2", "B. u5728u4f4eu9009u62e9u6027u5b57u6bb5uff08u5982u6027u522bu5b57u6bb5u53eau6709u7537/u5973uff09u4e0au7684u67e5u8be2", "C. u8303u56f4u67e5u8be2uff08BETWEEN, >, <uff09", "D. u8054u5408u7d22u5f15u7684u6700u5de6u524du7f00u67e5u8be2"]' AS BINARY);

UPDATE questions
SET options = '["A. INNER JOIN只返回两表中匹配的行", "B. LEFT JOIN返回左表所有行，右表无匹配则填NULL", "C. CROSS JOIN返回两表的笛卡尔积", "D. FULL OUTER JOIN只有PostgreSQL支持，MySQL不支持"]', updated_at = updated_at
WHERE id = 10
  AND CAST(title AS BINARY) = CAST('在SQL中，下列关于JOIN的描述正确的有哪些？（多选）' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. INNER JOINu53eau8fd4u56deu4e24u8868u4e2du5339u914du7684u884c", "B. LEFT JOINu8fd4u56deu5de6u8868u6240u6709u884cuff0cu53f3u8868u65e0u5339u914du5219u586bNULL", "C. CROSS JOINu8fd4u56deu4e24u8868u7684u7b1bu5361u5c14u79ef", "D. FULL OUTER JOINu53eau6709PostgreSQLu652fu6301uff0cMySQLu4e0du652fu6301"]' AS BINARY);

UPDATE questions
SET options = '["A. 唯一标识每一行记录", "B. 加速查询性能", "C. 维护两个表之间的引用完整性", "D. 限制列的取值范围"]', updated_at = updated_at
WHERE id = 15
  AND CAST(title AS BINARY) = CAST('在关系数据库中，外键（FOREIGN KEY）的作用是？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u552fu4e00u6807u8bc6u6bcfu4e00u884cu8bb0u5f55", "B. u52a0u901fu67e5u8be2u6027u80fd", "C. u7ef4u62a4u4e24u4e2au8868u4e4bu95f4u7684u5f15u7528u5b8cu6574u6027", "D. u9650u5236u5217u7684u53d6u503cu8303u56f4"]' AS BINARY);

UPDATE questions
SET options = '["A. 适用于任意序列", "B. 时间复杂度为 O(n)", "C. 要求序列必须有序", "D. 空间复杂度为 O(n)"]', updated_at = updated_at
WHERE id = 16
  AND CAST(title AS BINARY) = CAST('以下关于二分查找的描述，哪项是正确的？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u9002u7528u4e8eu4efbu610fu5e8fu5217", "B. u65f6u95f4u590du6742u5ea6u4e3a O(n)", "C. u8981u6c42u5e8fu5217u5fc5u987bu6709u5e8f", "D. u7a7au95f4u590du6742u5ea6u4e3a O(n)"]' AS BINARY);

UPDATE questions
SET options = '["A. COUNT(*)", "B. COUNT(列名)", "C. SUM(列名)", "D. AVG(列名)"]', updated_at = updated_at
WHERE id = 18
  AND CAST(title AS BINARY) = CAST('以下哪些 SQL 聚合函数会忽略 NULL 值？（多选）' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. COUNT(*)", "B. COUNT(u5217u540d)", "C. SUM(u5217u540d)", "D. AVG(u5217u540d)"]' AS BINARY);

UPDATE questions
SET options = '["A. 队列", "B. 栈", "C. 链表", "D. 堆"]', updated_at = updated_at
WHERE id = 27
  AND CAST(title AS BINARY) = CAST('以下哪种数据结构适合实现浏览器的前进/后退功能？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u961fu5217", "B. u6808", "C. u94feu8868", "D. u5806"]' AS BINARY);

UPDATE questions
SET options = '["A. 随机顺序", "B. 升序序列", "C. 降序序列", "D. 层序序列"]', updated_at = updated_at
WHERE id = 28
  AND CAST(title AS BINARY) = CAST('二叉搜索树（BST）中序遍历的结果是？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u968fu673au987au5e8f", "B. u5347u5e8fu5e8fu5217", "C. u964du5e8fu5e8fu5217", "D. u5c42u5e8fu5e8fu5217"]' AS BINARY);

UPDATE questions
SET options = '["A. 二叉搜索树", "B. 堆", "C. 哈希表", "D. B+树"]', updated_at = updated_at
WHERE id = 30
  AND CAST(title AS BINARY) = CAST('以下哪些是树形数据结构？（多选）' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u4e8cu53c9u641cu7d22u6811", "B. u5806", "C. u54c8u5e0cu8868", "D. B+u6811"]' AS BINARY);

UPDATE questions
SET options = '["A. O(1)", "B. O(log n)", "C. O(n)", "D. O(n²)"]', updated_at = updated_at
WHERE id = 31
  AND CAST(title AS BINARY) = CAST('哈希表在最坏情况下查找的时间复杂度是多少？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. O(1)", "B. O(log n)", "C. O(n)", "D. O(nu00b2)"]' AS BINARY);

UPDATE questions
SET options = '["A. O(n)", "B. O(n log n)", "C. O(n²)", "D. O(log n)"]', updated_at = updated_at
WHERE id = 32
  AND CAST(title AS BINARY) = CAST('快速排序的平均时间复杂度是？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. O(n)", "B. O(n log n)", "C. O(nu00b2)", "D. O(log n)"]' AS BINARY);

UPDATE questions
SET options = '["A. 快速排序", "B. 归并排序", "C. 堆排序", "D. B和C"]', updated_at = updated_at
WHERE id = 34
  AND CAST(title AS BINARY) = CAST('以下排序算法中，时间复杂度始终为 O(n log n) 的是？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u5febu901fu6392u5e8f", "B. u5f52u5e76u6392u5e8f", "C. u5806u6392u5e8f", "D. Bu548cC"]' AS BINARY);

UPDATE questions
SET options = '["A. 记忆化搜索（Memoization）", "B. 动态规划（自底向上）", "C. 矩阵快速幂", "D. 冒泡排序"]', updated_at = updated_at
WHERE id = 35
  AND CAST(title AS BINARY) = CAST('斐波那契数列 F(n) = F(n-1) + F(n-2) 用递归实现时，存在大量重复计算。以下哪些方法可以优化？（多选）' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u8bb0u5fc6u5316u641cu7d22uff08Memoizationuff09", "B. u52a8u6001u89c4u5212uff08u81eau5e95u5411u4e0auff09", "C. u77e9u9635u5febu901fu5e42", "D. u5192u6ce1u6392u5e8f"]' AS BINARY);

UPDATE questions
SET options = '["A. 进程是资源分配的基本单位，线程是CPU调度的基本单位", "B. 线程拥有独立的内存空间", "C. 进程比线程切换开销更小", "D. 一个线程只能属于一个进程，反之亦然"]', updated_at = updated_at
WHERE id = 37
  AND CAST(title AS BINARY) = CAST('进程和线程的主要区别是什么？' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u8fdbu7a0bu662fu8d44u6e90u5206u914du7684u57fau672cu5355u4f4duff0cu7ebfu7a0bu662fCPUu8c03u5ea6u7684u57fau672cu5355u4f4d", "B. u7ebfu7a0bu62e5u6709u72ecu7acbu7684u5185u5b58u7a7au95f4", "C. u8fdbu7a0bu6bd4u7ebfu7a0bu5207u6362u5f00u9500u66f4u5c0f", "D. u4e00u4e2au7ebfu7a0bu53eau80fdu5c5eu4e8eu4e00u4e2au8fdbu7a0buff0cu53cdu4e4bu4ea6u7136"]' AS BINARY);

UPDATE questions
SET options = '["A. 互斥条件", "B. 占有并等待", "C. 非抢占条件", "D. 循环等待"]', updated_at = updated_at
WHERE id = 38
  AND CAST(title AS BINARY) = CAST('操作系统中，死锁产生的四个必要条件包括哪些？（多选）' AS BINARY)
  AND CAST(options AS BINARY) = CAST('["A. u4e92u65a5u6761u4ef6", "B. u5360u6709u5e76u7b49u5f85", "C. u975eu62a2u5360u6761u4ef6", "D. u5faau73afu7b49u5f85"]' AS BINARY);
