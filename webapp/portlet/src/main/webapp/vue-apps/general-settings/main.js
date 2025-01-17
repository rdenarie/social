/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2020 - 2023 Meeds Association contact@meeds.io
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import './initComponents.js';

// get overrided components if exists
const components = extensionRegistry.loadComponents('GeneralSettings');
if (components && components.length > 0) {
  components.forEach(cmp => {
    Vue.component(cmp.componentName, cmp.componentOptions);
  });
}

const appId = 'generalSettings';
const lang = window.eXo?.env?.portal?.language || 'en';

// Should expose the locale ressources as REST API 
const urls = [
  `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.Login-${lang}.json`,
  `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portal.login-${lang}.json`,
  `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.Portlets-${lang}.json`,
  `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.GeneralSettings-${lang}.json`,
];

export function init(params) {
  exoi18n.loadLanguageAsync(lang, urls)
    .then(i18n =>
      Vue.createApp({
        data: {
          params: params,
        },
        template: `<portal-general-settings id="${appId}" :params="params" />`,
        vuetify: Vue.prototype.vuetifyOptions,
        i18n
      }, `#${appId}`, 'General Settings')
    );
}
