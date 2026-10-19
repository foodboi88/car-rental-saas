import { Component, computed, input } from '@angular/core';

import { ICON_PATHS, IconName } from './icon-paths';

@Component({
  selector: 'app-icon',
  template: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path [attr.d]="path()" /></svg>',
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
    svg {
      width: 20px;
      height: 20px;
      fill: none;
      stroke: currentColor;
      stroke-width: 1.8;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  protected readonly path = computed(() => ICON_PATHS[this.name()]);
}
