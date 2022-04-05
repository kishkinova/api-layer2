/* eslint-disable no-undef */
/* eslint-disable spaced-comment */
/// <reference types="Cypress" />

function login() {
    cy.visit(`${Cypress.env('catalogHomePage')}/#/`);
    cy.url().should('contain', '/login');

    const username = Cypress.env('username');
    const password = Cypress.env('password');

    cy.get('button[type="submit"').as('submitButton');

    cy.get('#username').type(username);
    cy.get('input[name="password"]').type(password);

    cy.get('@submitButton').click();
}

describe('>>> Wizard Dialog test', () => {

    it('Dialog test', () => {
        login();

        cy.get('.header').should('exist');

        // Test first enabler
        cy.get('#onboard-wizard-button').should('exist').click();
        cy.get('[role="menu"] > .MuiListItem-button').should('have.length', 6);
        cy.get('[role="menu"] > .MuiListItem-button').eq(0).click();
        cy.get('[role="dialog"]').should('exist');

        cy.get('#yaml-browser').should('exist');
        cy.get('#yaml-browser').attachFile('testEnabler1.yaml');
        
        cy.get('#yaml-file-text').should('exist');
        cy.get('#wizard-cancel-button').click();
        cy.get('[role="dialog"]').should('not.exist');

        // Test second enabler
        cy.get('#onboard-wizard-button').should('exist').click();
        cy.get('[role="menu"] > .MuiListItem-button').should('have.length', 6);
        cy.get('[role="menu"] > .MuiListItem-button').eq(1).click();
        cy.get('[role="dialog"]').should('exist');

        cy.get('#yaml-browser').should('exist');
        cy.get('#yaml-browser').attachFile('testEnabler2.yaml');
        
        cy.get('#yaml-file-text').should('exist');
        cy.get('#wizard-cancel-button').click();
        cy.get('[role="dialog"]').should('not.exist');

        // Test third enabler
        cy.get('#onboard-wizard-button').should('exist').click();
        cy.get('[role="menu"] > .MuiListItem-button').should('have.length', 6);
        cy.get('[role="menu"] > .MuiListItem-button').eq(2).click();
        cy.get('[role="dialog"]').should('exist');

        cy.get('#yaml-browser').should('exist');
        cy.get('#yaml-browser').attachFile('testEnabler3.yaml');
        
        cy.get('#yaml-file-text').should('exist');
        cy.get('#wizard-cancel-button').click();
        cy.get('[role="dialog"]').should('not.exist');

        // Test fourth enabler
        cy.get('#onboard-wizard-button').should('exist').click();
        cy.get('[role="menu"] > .MuiListItem-button').should('have.length', 6);
        cy.get('[role="menu"] > .MuiListItem-button').eq(3).click();
        cy.get('[role="dialog"]').should('exist');

        cy.get('#yaml-browser').should('exist');
        cy.get('#yaml-browser').attachFile('testEnabler4.yaml');
        
        cy.get('#yaml-file-text').should('exist');
        cy.get('#wizard-cancel-button').click();
        cy.get('[role="dialog"]').should('not.exist');

        // Test fifth enabler
        cy.get('#onboard-wizard-button').should('exist').click();
        cy.get('[role="menu"] > .MuiListItem-button').should('have.length', 6);
        cy.get('[role="menu"] > .MuiListItem-button').eq(4).click();
        cy.get('[role="dialog"]').should('exist');

        cy.get('#yaml-browser').should('exist');
        cy.get('#yaml-browser').attachFile('testEnabler5.yaml');
        
        cy.get('#yaml-file-text').should('exist');
        cy.get('#wizard-cancel-button').click();
        cy.get('[role="dialog"]').should('not.exist');
        
        /* Note: The sixth enabler mimics whichever enabler was open before it (defaulting as the first enabler), so there are no tests for it. */
    });
});
