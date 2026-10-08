-- Placeholder texts; the real legal text ships as V5 or later. Published migrations are never edited.
INSERT INTO consent_text_versions (version, requires_reconsent) VALUES (1, false);
INSERT INTO consent_texts (version, language, label, description, policy_url) VALUES
 (1, 'en', 'Allow optional data collection',
  'Help us improve the app by allowing optional data such as crash reports and usage statistics to be collected. You can change this at any time in Settings.',
  'https://example.com/privacy'),
 (1, 'tr', 'İsteğe bağlı veri toplamaya izin ver',
  'Çökme raporları ve kullanım istatistikleri gibi isteğe bağlı verilerin toplanmasına izin vererek uygulamayı geliştirmemize yardımcı olun. Bu tercihi istediğiniz zaman Ayarlar''dan değiştirebilirsiniz.',
  'https://example.com/privacy');
