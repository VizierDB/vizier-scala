from ast import AST, AnnAssign, Assert, Assign, AsyncFor, AsyncFunctionDef, AsyncWith, Attribute, AugAssign, Call, ClassDef, Delete, For, FunctionDef, If, Import, Name, Raise, Return, Try, Tuple, While, With, alias
import ast
from collections import defaultdict, deque
import json
from keyword import iskeyword
from textwrap import dedent
from typing import Any, Iterator
import typing


class Cell_Scope:
    INSIDE  = "inside" 
    OUTSIDE = "outside"
    EITHER  = "either"

class Visit_AST(ast.NodeVisitor, Cell_Scope):
    def __init__(self):
        self.main_dict_store = list()
        self.main_dict_load = list()
        self.outside_reads = list()
        self.scope_stack = []
        self.reads_stack = deque()
        self.values = {}

        self.scope_stack.append({})
    
    def visit_FunctionDef(self, node: FunctionDef) -> Any:
        ## Add function to current stack
        # self.scope_stack[0][node.name] = (self.INSIDE, []) 
        self.scope_stack[0][node.name] = 0 
        ## Add a new scope for the function vars
        self.scope_stack.append({}) 
        ## Visit the body
        super().generic_visit(node)

        self.scope_stack.pop() 

    def visit_AsyncFunctionDef(self, node: AsyncFunctionDef) -> Any:
        ## Add function to current stack
        self.scope_stack[0][node.name] = (self.INSIDE, []) 
        ## Add a new scope for the function vars
        self.scope_stack.append({}) 
        ## Visit the body
        super().generic_visit(node)

        self.scope_stack.pop()  

    # ?? 
    def visit_Delete(self, node: Delete) -> Any:
        for target in node.targets:
            self.generic_visit(target)

    def visit_For(self, node: For) -> Any:
        ## If it enters a new scope, make a new scope with the current node
        scope = {node.target.id: self.INSIDE}
        self.scope_stack.append(scope)
        super().generic_visit(node)
        self.scope_stack.pop()

    def visit_AsyncFor(self,node: AsyncFor) -> Any:
        scope = {node.target.id: self.INSIDE}
        self.scope_stack.append(scope)
        super().generic_visit(node)
        self.scope_stack.pop()
    
    def visit_If(self, node: If) -> Any:                            
        super().generic_visit(node)
        # ?
        # for i in range(len(node.body)):
        #     # print(node.body[0].targets[0].id)
        #     super().generic_visit(node.body[i])

        # if node.orelse:
        #     for i in range(len(node.orelse)):
        #         super().generic_visit(node.orelse[i])
     
    
    def visit_While(self, node: While) -> Any:
        scope = {node.test.lineno: self.INSIDE}
        self.scope_stack.append(scope)
        self.generic_visit(node.test)
        self.scope_stack.pop()

        for i in range(len(node.body)):
            scope = {node.body[i].lineno: self.INSIDE}
            self.scope_stack.append(scope)
            self.generic_visit(node.body[i])
            self.scope_stack.pop()
        
        if node.orelse:
            for i in range(len(node.orelse)):
                scope = {node.orelse[i].lineno: self.INSIDE}
                self.scope_stack.append(scope)
                self.generic_visit(node.orelse[i])
                self.scope_stack.pop()

    def visit_Assign(self, node: Assign) -> Any: ## NOT DONE
        if isinstance(node.value, ast.Constant): ## If its a value assign the value
            for target in node.targets:
                # print(target.id)
                super().generic_visit(target)
                self.main_dict_store.append(target.id) 
                self.scope_stack[0][target.id] = node.value.value
        elif isinstance(node.value, ast.Name): ## if its a Variable get the value from the variable
            super().generic_visit(node)
            # for target in node.targets:
            #     super().generic_visit(target)
            #     self.main_dict_store.append(target.id) 
            #     # self.scope_stack[0][target.id] = self.scope_stack[0][node.value.id]
            #     # self.scope_stack[0][target.id] = node.value.id
            #     self.scope_stack[0][target.id] = -1 # test dont' use


        else:
            super().generic_visit(node)
        # super().generic_visit(node)

    def visit_AugAssign(self, node: AugAssign) -> Any:
        super().generic_visit(node)

    def visit_AnnAssign(self, node: AnnAssign) -> Any:
        super().generic_visit(node)

    # Make this into a function of some sort ?? #
    def visit_Name(self, node: Name) -> Any:
        if isinstance(node.ctx, ast.Store) :
            ## Using this because what if we're curreingly in a scope and use a var defined in another scope
            self.main_dict_store.append(node.id) 
            # self.scope_stack[0][node.id] = self.INSIDE
        if isinstance(node.ctx, ast.Load) and node.id not in self.main_dict_store and not iskeyword(node.id):
            # self.scope_stack[0][node.id] = self.OUTSIDE
            self.outside_reads.append(node.id)
        # If we get something in a function that is declared outside the scope of the function add it to deps
        # if  (node.id not in self.scope_stack[0]) and (node.id in self.scope_stack[1]):
        #     for name in self.scope_stack[1]:
        #         if isinstance(self.scope_stack[1][name], tuple):
        #             self.scope_stack[1][name][1].append(node.id)

    def visit_Call(self, node: Call) -> Any:
        ## We need to figure out how to deal with function call
        if isinstance(node.func,ast.Attribute):
            super().generic_visit(node)
        for arg in node.args:
            self.visit(arg)

    def visit_Attribute(self, node: Attribute) -> Any:
        if isinstance(node.value, ast.Name):
            super().generic_visit(node)
            
    
    def visit_Import(self, node: Import) -> Any:
        super().generic_visit(node)
    
    def visit_alias(self, node: alias) -> Any:
        if node.asname != None:
            self.scope_stack[0][node.asname] = node.name
        else:
            self.scope_stack[0][node.name] = node.name
            


    
def analyze(script: str) -> typing.Tuple[list, dict[str,int]]:
# def analyze(script: str) -> list:
    tree = ast.parse(script)
    vis = Visit_AST()
    vis.visit(tree)    
    # print(ast.dump(tree, indent=4))

    # print(vis.outside_reads)
    # print(vis.scope_stack[0])
    return vis.outside_reads, vis.scope_stack[0]

def main() -> Any :
    source = open("../../../test_data/dependency_test/test.py", "r")
    # (deps, writes) = analyze(dedent("""x=5
                            #  print(x)""".strip()))
    deps, writes = analyze(source.read())
    print(deps, writes)

            
        
if __name__ == '__main__':
   main()
