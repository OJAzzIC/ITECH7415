// 'Standard' includes
{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

// Common plans, goals & beliefs for multiple agents

/************************************************************/
/* These two plans handle a list of items to print to the console */
// Handle a non-empty list by splitting into 2 parts: 1st element and all remaining elements
// Process 1st element
// Recursively call to process remaining elements.
+!print_listItems([T|Rest]) <-
    .print(T);
    !print_listItems(Rest);
    .
// The degenerate/end case of an empty list.
+!print_listItems([]).
/************************************************************/
