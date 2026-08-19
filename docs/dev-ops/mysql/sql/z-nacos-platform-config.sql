-- 七服务拓扑的初始 Nacos DataId。
-- INSERT IGNORE 有意保留复用卷中由管理员维护的值。
-- 学习环境事实来源使用空 tenant_id（Nacos 3.x 默认命名空间的写入目标）。
USE `nacos_config`;

INSERT IGNORE INTO `config_info`
(`data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`, `encrypted_data_key`)
VALUES
(
  'big-market-platform-config',
  'DEFAULT_GROUP',
  'chatbot.enabled.value=true\nchatbot.enabled.description=Chatbot entrance switch\nchatbot.provider.value=local\nchatbot.provider.description=Provider local deepseek openai\nchatbot.apiKey.value=\nchatbot.apiKey.description=LLM provider API key\nchatbot.baseUrl.value=https://api.deepseek.com\nchatbot.baseUrl.description=LLM provider base URL\nchatbot.model.value=deepseek-chat\nchatbot.model.description=LLM model name\nchatbot.costPerAsk.value=0\nchatbot.costPerAsk.description=Credits charged only for configured remote provider\nactivity.100301.state.value=closed\nactivity.100301.title.value=Lucky raffle activity (closed)\nactivity.100301.copy.value=This legacy activity is unavailable\nactivity.100401.state.value=online\nactivity.100401.title.value=OpenAI raffle activity\nactivity.100401.copy.value=Login to join the raffle\n',
  MD5('chatbot.enabled.value=true\nchatbot.enabled.description=Chatbot entrance switch\nchatbot.provider.value=local\nchatbot.provider.description=Provider local deepseek openai\nchatbot.apiKey.value=\nchatbot.apiKey.description=LLM provider API key\nchatbot.baseUrl.value=https://api.deepseek.com\nchatbot.baseUrl.description=LLM provider base URL\nchatbot.model.value=deepseek-chat\nchatbot.model.description=LLM model name\nchatbot.costPerAsk.value=0\nchatbot.costPerAsk.description=Credits charged only for configured remote provider\nactivity.100301.state.value=closed\nactivity.100301.title.value=Lucky raffle activity (closed)\nactivity.100301.copy.value=This legacy activity is unavailable\nactivity.100401.state.value=online\nactivity.100401.title.value=OpenAI raffle activity\nactivity.100401.copy.value=Login to join the raffle\n'),
  NOW(), NOW(), '', 'Initial platform configuration', '', '', 'properties', '', ''
),
(
  'big-market-runtime-switches',
  'DEFAULT_GROUP',
  'system.degradeSwitch.value=close\nsystem.degradeSwitch.description=Global raffle degrade switch\nsystem.rateLimiterSwitch.value=close\nsystem.rateLimiterSwitch.description=Global rate limiter switch\n',
  MD5('system.degradeSwitch.value=close\nsystem.degradeSwitch.description=Global raffle degrade switch\nsystem.rateLimiterSwitch.value=close\nsystem.rateLimiterSwitch.description=Global rate limiter switch\n'),
  NOW(), NOW(), '', 'Initial runtime switches', '', '', 'properties', '', ''
);
