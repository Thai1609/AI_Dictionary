export interface ExampleItem {
  sentence?: string | null;
  reading?: string | null;
  translation?: string | null;
}

export interface TranslationItem {
  word?: string | null;
  pronunciation?: string | null;
  reading?: string | null;
  partOfSpeech?: string | null;
  meanings?: string[] | null;
  usage?: string | null;
  examples?: ExampleItem[] | null;
  relatedWords?: string[] | null;
  note?: string | null;
}

export interface TranslationGroup {
  partOfSpeech?: string | null;
  items?: TranslationItem[] | null;
}

export interface DictionaryRecommendation {
  defaultWord?: string | null;
  partOfSpeech?: string | null;
  reason?: string | null;
}

export interface DictionaryResult {
  word?: string | null;
  pronunciation?: string | null;
  reading?: string | null;
  partOfSpeech?: string | null;
  meanings?: string[] | null;
  examples?: ExampleItem[] | null;
  relatedWords?: string[] | null;
  note?: string | null;
  translationGroups?: TranslationGroup[] | null;
  recommendation?: DictionaryRecommendation | null;
}

export interface AnalyzeResponse {
  type?: "word" | "sentence" | "grammar" | string;
  dictionary?: DictionaryResult | null;
  grammar?: unknown;
  message?: string | null;
}
