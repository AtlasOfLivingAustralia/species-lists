export class ListError extends Error {
  readonly title: string;
  readonly breadcrumb: string;

  constructor(message: string, title: string, breadcrumb: string) {
    super(message);
    this.name = 'ListError';
    this.title = title;
    this.breadcrumb = breadcrumb;
  }
}
