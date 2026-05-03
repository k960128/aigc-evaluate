import * as mockApi from './mock'
import * as evalTaskApi from './eval-task'
import * as modelApi from './model'
import * as manufacturerApi from './manufacturer'

const isMock = import.meta.env.VITE_APP_ENV === 'mock'

function wrapMockResponse<T>(mockFn: (...args: any[]) => Promise<T>) {
  return async (...args: any[]) => {
    const result = await mockFn(...args)
    return { data: result }
  }
}

export const createEvalTask = isMock 
  ? wrapMockResponse(mockApi.mockCreateTask) 
  : evalTaskApi.createEvalTask

export const getEvalTaskList = isMock 
  ? wrapMockResponse(mockApi.mockGetTaskList) 
  : evalTaskApi.getEvalTaskList

export const getEvalTaskProgress = isMock 
  ? wrapMockResponse(mockApi.mockGetTaskDetail) 
  : evalTaskApi.getEvalTaskProgress

export const submitEvalTask = isMock 
  ? wrapMockResponse(mockApi.mockCreateTask) 
  : evalTaskApi.submitEvalTask

export const getModelList = isMock 
  ? wrapMockResponse(mockApi.mockGetModels) 
  : modelApi.getModelList

export const getManufacturerList = isMock 
  ? wrapMockResponse(mockApi.mockGetManufacturerList) 
  : manufacturerApi.getManufacturerList

export const createManufacturer = isMock 
  ? wrapMockResponse(mockApi.mockCreateManufacturer) 
  : manufacturerApi.createManufacturer

export const updateManufacturer = isMock 
  ? wrapMockResponse(mockApi.mockUpdateManufacturer) 
  : manufacturerApi.updateManufacturer

export const deleteManufacturer = isMock 
  ? wrapMockResponse(mockApi.mockDeleteManufacturer) 
  : manufacturerApi.deleteManufacturer
