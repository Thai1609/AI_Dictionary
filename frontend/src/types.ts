export interface DictionaryExample {
  sentence?: string | null;
  reading?: string | null;
  translation?: string | null;
}

export interface TranslationOption {
  word?: string | null;
  pronunciation?: string | null;
  reading?: string | null;
  partOfSpeech?: string | null;
  meanings?: string[] | null;
  usage?: string | null;
  examples?: DictionaryExample[] | null;
  relatedWords?: string[] | null;
  note?: string | null;
}

export interface TranslationGroup {
  partOfSpeech?: string | null;
  items?: TranslationOption[] | null;
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
  examples?: DictionaryExample[] | null;
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
