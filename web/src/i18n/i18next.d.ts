// Type-checks translation keys against the English resources, which define the full key set.
import "i18next";
import type en from "./en.json";

declare module "i18next" {
  interface CustomTypeOptions {
    defaultNS: "translation";
    resources: { translation: typeof en };
  }
}
