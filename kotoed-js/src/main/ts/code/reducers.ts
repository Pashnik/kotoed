import * as _ from "lodash"

import {EditorState, FileTreeState} from "./state";
import {Action} from "redux";
import {isType} from "typescript-fsa";
import {dirCollapse, dirExpand, dirFetch, fileFetch, fileSelect, rootFetch} from "./actions";
import {
    collapseDir, expandDir, expandEverything, makeBlueprintTreeState, selectFile,
    unselectFile
} from "./util/filetree";

const initialFileTreeState: FileTreeState = {
    nodes: [],
    loading: true,
    selectedPath: []
};



export const fileTreeReducer = (state: FileTreeState = initialFileTreeState, action: Action) => {
    if (isType(action, dirExpand)) {
        let newState = _.cloneDeep(state);
        expandEverything(newState.nodes, action.payload.treePath);
        return newState;
    } else if (isType(action, dirCollapse)) {
        let newState = _.cloneDeep(state);
        collapseDir(newState.nodes, action.payload.treePath);
        return newState;
    } else if (isType(action, fileSelect)) {
        let newState = _.cloneDeep(state);
        unselectFile(newState.nodes, newState.selectedPath);
        expandEverything(newState.nodes, action.payload.treePath);
        selectFile(newState.nodes, action.payload.treePath);
        newState.selectedPath = action.payload.treePath;
        return newState;
    } else if (isType(action, rootFetch.done)) {
        let newState = {...state};
        newState.nodes = makeBlueprintTreeState(action.payload.result.list);
        newState.loading = false;
        return newState;
    }
    return state;
};

export const editorReducer = (state: EditorState = {value: "", fileName: ""}, action: Action) => {
    if (isType(action, fileFetch.done)) {
        let newState = {...state};
        newState.value = action.payload.result.value;
        newState.fileName = action.payload.params.filename;
        return newState;
    }
    return state;
};