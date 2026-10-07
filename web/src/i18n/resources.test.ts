import { describe, expect, it } from "vitest";
import en from "./en.json";
import { LOCALES } from "./locale";
import ru from "./ru.json";

const RESOURCES: Record<(typeof LOCALES)[number], unknown> = { en, ru };

const PLURAL_SUFFIX = /_(zero|one|two|few|many|other)$/;

interface Leaves {
  /** Every leaf key with plural suffixes removed, so that languages with different plural categories compare equal. */
  keys: Set<string>;
  /** The plural categories found under each pluralised key. */
  plurals: Map<string, Set<string>>;
}

function collect(value: unknown, prefix: string, into: Leaves): void {
  if (typeof value === "string") {
    const match = PLURAL_SUFFIX.exec(prefix);
    if (!match) {
      into.keys.add(prefix);
      return;
    }
    const base = prefix.slice(0, -match[0].length);
    into.keys.add(base);
    const categories = into.plurals.get(base) ?? new Set<string>();
    categories.add(match[1]!);
    into.plurals.set(base, categories);
    return;
  }
  expect(value, `${prefix} must be an object or a string`).toBeTypeOf("object");
  expect(value, `${prefix} must not be null`).not.toBeNull();
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    collect(child, prefix ? `${prefix}.${key}` : key, into);
  }
}

function leaves(resource: unknown): Leaves {
  const into: Leaves = { keys: new Set(), plurals: new Map() };
  collect(resource, "", into);
  return into;
}

describe("translation resources", () => {
  const english = leaves(en);

  it("have the same keys in every language", () => {
    for (const locale of LOCALES) {
      const found = leaves(RESOURCES[locale]);
      expect([...found.keys].sort(), `keys of ${locale}.json`).toEqual([...english.keys].sort());
    }
  });

  it("pluralise the same keys and carry every plural category of their language", () => {
    for (const locale of LOCALES) {
      const found = leaves(RESOURCES[locale]);
      expect([...found.plurals.keys()].sort(), `plural keys of ${locale}.json`).toEqual([...english.plurals.keys()].sort());
      const required = new Intl.PluralRules(locale).resolvedOptions().pluralCategories;
      for (const [key, categories] of found.plurals) {
        expect([...categories].sort(), `plural forms of ${key} in ${locale}.json`).toEqual([...required].sort());
      }
    }
  });

  it("have no empty strings", () => {
    for (const locale of LOCALES) {
      const check = (value: unknown, path: string) => {
        if (typeof value === "string") expect(value.trim(), `${locale}.json: ${path}`).not.toBe("");
        else for (const [key, child] of Object.entries(value as Record<string, unknown>)) check(child, `${path}.${key}`);
      };
      check(RESOURCES[locale], locale);
    }
  });
});
